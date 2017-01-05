package com.codecommit.antixml

import monocle.function.Index
import monocle.macros.GenLens
import monocle.{Lens, Optional, Prism}

import scala.language.{dynamics, higherKinds}

object NodeOptics extends App {
  final lazy val node2elem: Prism[Node, Elem] = {
    Prism.partial[Node, Elem] { case e: Elem => e }(identity)
  }

  final lazy val gnode2gelem: Prism[Group[Node], Group[Elem]] = {
    Prism[Group[Node], Group[Elem]](gn => Some(gn.collect { case e: Elem => e }))(identity)
  }

  final lazy val elem2Children: Lens[Elem, Group[Node]] = GenLens[Elem](_.children)

  final lazy val elem2ElemChildren: Optional[Elem, Group[Elem]] = elem2Children.composePrism(gnode2gelem)

  final def gelem2elem(name: String): Optional[Group[Elem], Elem] = {
    Optional[Group[Elem], Elem](_.find(_.name == name))(e => ge => ge.filterNot(_.name == name).+:(e))
  }

  final lazy val elem2Label: Lens[Elem, String] = GenLens[Elem](_.name)

  final lazy val node2Children: Optional[Node, Group[Node]] = node2elem.composeLens(elem2Children)

  final lazy val node2ElemChildren: Optional[Node, Group[Elem]] = node2Children.composePrism(gnode2gelem)

  implicit val gIndex = new Index[Group[Elem], String, Group[Elem]] {
    override def index(name: String): Optional[Group[Elem], Group[Elem]] = {
      Optional[Group[Elem], Group[Elem]](ge => Some(ge.filter(_.name == name)))(ge => gei => ge)
    }
  }

}

final case class XMLPathChildren(elem: Optional[Elem, Group[Elem]]) extends Dynamic {

  import NodeOptics._

  def selectDynamic(field: String): XMLPath = XMLPath(elem.composeOptional(gelem2elem(field)))

  def modifyEach(f: Elem => Elem)(input: Elem): Option[Elem] = elem.modifyOption(_.map(f))(input)

  def modifyEachOpt(f: Elem => Option[Elem])(input: Elem): Option[Elem] = {
    elem.modifyOption(_.map(e => f(e).fold(e)(identity)))(input)
  }

  def set(elemToSet: Elem)(input: Elem): Option[Elem] = modifyEach(_ => elemToSet)(input)

  def add(elemToSet: Elem)(input: Elem): Option[Elem] = {
    val fn = Optional[Group[Elem], Group[Elem]](ge => Some(ge.+:(elemToSet)))(gei => ge => gei)

    elem.composeOptional(fn)
      .getOption(input)
      .map(updated => elem.set(updated)(input))
  }

  def getOption(input: Elem): Option[Group[Elem]] = elem.getOption(input)
}

final case class XMLPath(elem: Optional[Elem, Elem]) {

  import NodeOptics._

  lazy val children: XMLPathChildren = XMLPathChildren(elem.composeOptional(elem2ElemChildren))

  def modify(f: Elem => Elem)(input: Elem): Option[Elem] = {
    val fn = Optional[Elem, Elem](e => Some(f(e)))(ei => e => ei)

    elem.composeOptional(fn)
      .getOption(input)
      .map(updated => elem.set(updated)(input))
  }

  def set(elemToSet: Elem)(input: Elem): Option[Elem] = modify(_ => elemToSet)(input)

  def getOption(input: Elem): Option[Elem] = elem.getOption(input)
}

object XMLPath {
  val root: XMLPath = XMLPath(Optional.id[Elem])
}
