package com.codecommit.antixml

import com.codecommit.antixml.XMLPath._
import org.specs2.mutable._

class XMLPathSpecs extends Specification {

  "XMLPath" should {

    "return all the document when the we ask the root" in {
      val input =
        <A Attr="01234">
          <B>B</B>
        </A>.convert

      root.getOption(input) mustEqual Some(input)
    }

    "return the child" in {
      val input =
        <A Attr="01234">
          <B>B</B>
        </A>.convert

      root.children.getOption(input) mustEqual Some(Group(<B>B</B>.convert))
    }

    "return the selected child" in {
      val input =
        <A Attr="01234">
          <B>B</B>
        </A>.convert

      root.children.B.getOption(input) mustEqual Some(<B>B</B>.convert)
    }

    "return the selected child using find" in {
      val input =
        <A Attr="01234">
          <B>B</B>
        </A>.convert

      root.find("B").getOption(input) mustEqual Some(<B>B</B>.convert)
    }

    "return the selected nested child" in {
      val input =
        <A Attr="01234">
          <B>
            <C Attr="C"></C>
          </B>
        </A>.convert

      root.children.B.children.C.getOption(input) mustEqual Some(<C Attr="C"></C>.convert)
    }

    "return the selected nested child using find" in {
      val input =
        <A Attr="01234">
          <B>
            <C Attr="C"></C>
          </B>
        </A>.convert

      root.find("B").find("C").getOption(input) mustEqual Some(<C Attr="C"></C>.convert)
    }

    "return nothing if the selected nested child is missing" in {
      val input =
        <A Attr="01234">
          <B>B</B>
        </A>.convert

      root.children.B.children.C.getOption(input) mustEqual None
    }

    "set a node" in {
      val input =
        <A Attr="01234">
          <B>B</B>
        </A>.convert

      root.children.set(<Other/>.convert)(input) mustEqual Some(<A Attr="01234"><Other/></A>.convert)
    }

    "set a node multiple times" in {
      val input =
        <A Attr="01234">
          <B>B</B>
          <B>B</B>
        </A>.convert

      root.children.set(<Other/>.convert)(input) mustEqual Some(<A Attr="01234"><Other/><Other/></A>.convert)
    }

    "set a node in a nested child" in {
      val input =
        <A Attr="01234">
          <B Attr="b"><C/></B>
        </A>.convert

      root.children.B.children.set(<Other/>.convert)(input) mustEqual Some(<A Attr="01234"><B Attr="b"><Other/></B></A>.convert)
    }

    "modify a node in a nested child" in {
      val input =
        <A Attr="01234">
          <B>
            <C Attr="Yep"/>
            <C Attr="Nope"/>
          </B>
        </A>.convert

      val res = root.children.B.children
        .modifyEach(_.addAttributes(Seq(("OtherAttr", "bar"))))(input)

      res mustEqual Some(<A Attr="01234"><B><C Attr="Yep" OtherAttr="bar"/><C Attr="Nope" OtherAttr="bar"/></B></A>.convert)
    }

    "modify a nested node" in {
      val input =
        <A Attr="01234">
          <B>
            <C Attr="Yep"></C>
          </B>
        </A>.convert

      val res = root.children.B.children.C
        .modify(_.addAttributes(Seq(("OtherAttr", "bar"))))(input)

      res mustEqual Some(<A Attr="01234"><B><C Attr="Yep" OtherAttr="bar"/></B></A>.convert)
    }

    "modify a missing node" in {
      val input =
        <A Attr="01234">
          <C></C>
        </A>.convert

      val res = root.children.Missing
        .modify(_.addAttributes(Seq(("OrderLinesAttr", "hello"))))(input)

      res mustEqual None
    }

    "modify each node optionally" in {
      val input =
        <A Attr="01234">
          <B>
            <C Attr="Yep"></C>
            <C Attr="Nope"></C>
          </B>
        </A>.convert

      val res = root.children.B.children
        .modifyEachOpt(x =>
          if (x.attr("Attr").contains("Yep")) Some(x.addAttributes(Seq(("OtherAttr", "bar"))))
          else None
      )(input)

      res mustEqual Some(<A Attr="01234"><B><C Attr="Yep" OtherAttr="bar"/><C Attr="Nope"/></B></A>.convert)
    }

  }

}
