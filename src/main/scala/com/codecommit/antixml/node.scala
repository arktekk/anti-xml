/*
 * Copyright (c) 2011, Daniel Spiewak
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this
 *   list of conditions and the following disclaimer in the documentation and/or
 *   other materials provided with the distribution.
 * - Neither the name of "Anti-XML" nor the names of its contributors may
 *   be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.codecommit.antixml

import scala.collection.immutable.{Iterable, Map}
import scala.language.higherKinds
import scalaz.{Applicative, Traverse}

/**
  * Root of the `Node` ADT, representing the different types of supported XML
  * nodes which may appear in an XML fragment.  The ADT itself has the following
  * shape (Haskell syntax):
  *
  * {{{
  * type Prefix = Maybe String
  * type Scope = Map String String
  *
  * data Node = ProcInstr String String
  *           | Elem Prefix String Attributes Scope (Group Node)
  *           | Text String
  *           | CDATA String
  *           | EntityRef String
  * }}}
  *
  * For those that don't find Haskell to be the clearest explanation of what's
  * going on in this type, here is a more natural-language version.  The `Node`
  * trait is sealed and has exactly four subclasses, each implementing a different
  * type of XML node.  These four classes are as follows:
  *
  * <ul>
  * <li>[[com.codecommit.antixml.ProcInstr]] – A processing instruction consisting
  * of a target and some data</li>
  * <li>[[com.codecommit.antixml.Elem]] – An XML element consisting of an optional
  * prefix, a name (or identifier), a set of attributes, a set of namespace mappings
  * in scope and a sequence of child nodes</li>
  * <li>[[com.codecommit.antixml.Text]] – A node containing a single string, representing
  * character data in the XML tree</li>
  * <li>[[com.codecommit.antixml.CDATA]] – A node containing a single string, representing
  * ''unescaped'' character data in the XML tree</li>
  * <li>[[com.codecommit.antixml.EntityRef]] – An entity reference (e.g. `&amp;`)</li>
  * </ul>
  */
sealed trait Node {
  /**
    * Returns the children of this node. If the node is an [[com.codecommit.antixml.Elem]],
    * then this method returns the element's children.  Otherwise, it returns an empty
    * [[com.codecommit.antixml.Group]].
    */
  def children = Group.empty[Node]
}

private[antixml] object Node {

  /* http://www.w3.org/TR/xml/#NT-Char */
  private[this] val CharRegex =
  """[\u0009\u000A\u000D\u0020-\uD7FF\uE000-\uFFFD\x{10000}-\x{10FFFF}]*""".r

  def hasOnlyValidChars(value: String) = CharRegex.pattern.matcher(value).matches

  // TODO we should probably find a way to propagate custom entities from DTDs
  /* http://www.w3.org/TR/xml/#NT-CharData */
  def escapeText(text: String) = text flatMap {
    case '&' => "&amp;"
    case '<' => "&lt;"
    case '>' => "&gt;" // text may not contain ]]>, this is a way to avoid that
    case c => List(c)
  }

  /* http://www.w3.org/TR/xml/#NT-AttValue */
  def quoteAttribute(value: String) = {
    if (value.contains("\"")) {
      "'" + (value flatMap {
        case '&' => "&amp;"
        case '<' => "&lt;"
        case '\'' => "&apos;"
        case c => List(c)
      }) + "'"
    } else {
      "\"" + (value flatMap {
        case '&' => "&amp;"
        case '<' => "&lt;"
        case '"' => "&quot;"
        case c => List(c)
      }) + "\""
    }
  }

  def isElemWithName(name: String)(node: Node): Boolean = {
    node match {
      case e: Elem if e.name == name => true
      case _ => false
    }
  }
}

/**
  * A processing instruction consisting of a `target` and some `data`.  For example:
  *
  * {{{
  * <?xml version="1.0"?>
  * }}}
  *
  * This would result in the following node:
  *
  * {{{
  * ProcInstr("xml", "version=\"1.0\"")
  * }}}
  */
case class ProcInstr(target: String, data: String) extends Node {
  override def toString = "<?" + target + " " + data + "?>"
}

/**
  * An XML element consisting of an optional namespace prefix, a name (or identifier), a
  * set of attributes, a namespace prefix scope (mapping of prefixes to namespace URIs),
  * and a sequence of child nodes.
  * For example:
  *
  * {{{
  * <span id="foo" class="bar">Lorem ipsum</span>
  * }}}
  *
  * This would result in the following node:
  *
  * {{{
  * Elem(None, "span", attrs = Attributes("id" -> "foo", "class" -> "bar"), children = Group(Text("Lorem ipsum")))
  * }}}
  * TODO: Consider making Elem not a case class and handle thing a different way.
  */
case class Elem(prefix: Option[String], name: String, attrs: Attributes = Attributes(), namespaces: NamespaceBinding = NamespaceBinding.empty, override val children: Group[Node] = Group.empty) extends Node with Selectable[Elem] {
  //TODO: adding the empty namespacebinding is a hack because we cannot know which ns the attributes are supposed to be ahead of time.
  if (!Elem.isValidName(name)) {
    throw new IllegalArgumentException("Illegal element name, '" + name + "'")
  }
  if (!prefix.forall(Elem.isValidName(_))) {
    throw new IllegalArgumentException("Illegal element prefix, '" + prefix.getOrElse("") + "'")
  }
  Elem.validateAttributes(attrs, namespaces)

  /**
    * See the `canonicalize` method on [[com.codecommit.antixml.Group]].
    */
  def canonicalize = copy(children = children.canonicalize)

  override def toString = {
    val sw = new java.io.StringWriter()
    val xs = XMLSerializer()
    xs.serialize(this, sw)
    sw.toString
  }

  def writeTo(writer: java.io.Writer, charset: java.nio.charset.Charset = java.nio.charset.Charset.forName("UTF-8")) {
    try {
      XMLSerializer(charset.name()).serializeDocument(this, writer)
    }
    finally {
      writer.close()
    }
  }

  def toGroup = Group(this)

  def withName(name: String) = copy(name = name)

  /**
    * Convenience method to allow adding attributes in a chaining fashion.
    */
  def withAttribute(attr: (QName, String)) = copy(attrs = attrs + attr)

  def addAttributes(attrs: Seq[(QName, String)]) = copy(attrs = this.attrs ++ attrs)

  def removeAttribute(attr: QName) = removeAttributes(Seq(attr))

  def removeAttributes(attributes: Seq[QName]) = copy(attrs = attrs.filterNot(attr => attributes.contains(attr._1)))

  def modifyAttributes(attributes: Attributes) = removeAttributes(attributes.keys.toSeq).addAttributes(attributes.toSeq)

  def modifyAttribute(attr: (QName, String)) = modifyAttributes(Attributes(attr))

  /**
    * Use the uri in the FQName to lookup the binding, otherwise add a namespace with
    * the given default prefix. If the prefix/no prefix is already in use create a new name.
    */
  def setAttributes(attrs: Seq[(FQName, Option[String])]): Elem = {
    val (ns, at) = attrs.foldLeft((namespaces, this.attrs)) {
      case ((ns, at), (FQName(uri, local, defaultPrefix), value)) =>
        val (prefix, newNamespaces) = ns.findByUri(uri) match {
          case None =>
            val newPrefix = ns.findByPrefix(defaultPrefix.getOrElse("")) match {
              case None => defaultPrefix
              case Some(_) => Some(nextValidPrefix(ns))
            }
            (newPrefix, newPrefix.fold(ns.append(uri))(np => ns.append(np, uri)))
          case Some(binding) => (binding.prefix, ns)
        }
        val qn = QName(prefix, local)
        (newNamespaces, value.fold(at - qn)(v => at.updated(qn, v)))
    }
    copy(namespaces = ns, attrs = at)
  }

  def withAttributes(attrs: Attributes) = copy(attrs = attrs)

  def attr(name: QName) = attrs.get(name)

  def attr(fqname: FQName) = namespaces.collectFirst {
    case nb if (attrs.contains(QName(nb.prefix, fqname.local))) => attrs(QName(nb.prefix, fqname.local))
  }

  /**
    * Convenience method to allow adding a single child in a chaining fashion.
    */
  def addChild(node: Node) = copy(children = children :+ node)

  /**
    * Convenience method to allow adding children in a chaining fashion.
    */
  def addChildren(newChildren: Group[Node]) = copy(children = children ++ newChildren)

  /**
    * Convenience method to allow replacing all children in a chaining fashion.
    */
  def withChildren(children: Group[Node]) = copy(children = children)

  /**
    * Adds a namespace with a given prefix
    */
  def addNamespace(prefix: String, uri: String) = addNamespaces(Map(prefix -> uri))

  def addNamespace(uri: String) = copy(namespaces = namespaces.append(uri))

  // TODO
  def traverse[F[_]](f: Node => F[Node])(implicit F: Applicative[F]): F[Elem] = {
    F.map(
      children.foldLeft(F.pure(Group.empty[Node])) {
        case (acc, cur) => F.apply2(acc, f(cur))(_ :+ _)
      }
    )(mappedChildren => copy(children = mappedChildren))
  }

  private def nextValidPrefix(ns: NamespaceBinding) = {
    var i = 1
    while (ns.findByPrefix("ns" + i).isDefined) {
      i = i + 1
    }
    "ns" + i
  }

  /**
    * Adds the Map of prefix -> namespace to the scope.
    * If the prefix is the empty prefix, a new prefix is created for it.
    * If the namespace has been already registered, this will not re-register it.
    * (It is allowed by the XML spec, but kind of pointless in practice)
    *
    */
  def addNamespaces(namespaces: Map[String, String]) = {
    if (namespaces.isEmpty) this
    else {

      def mapit(binding: NamespaceBinding, tuple: (String, String)) = tuple match {
        case (_, uri) if (binding.findByUri(uri).isDefined) => binding
        case ("", uri) if (binding.findByPrefix("").isEmpty) => binding.append(uri) //if the empty namespace has not been defined already
        case ("", uri) => {
          val p = nextValidPrefix(this.namespaces)
          binding.append(p, uri)
        }
        case (pfx, uri) => binding.append(pfx, uri)
      }

      val binding = namespaces.foldLeft(this.namespaces) { case (ns, tuple) => mapit(ns, tuple) }
      if (binding == this.namespaces) this else copy(namespaces = binding)
    }
  }
}

object Elem {
  private[this] val NameRegex = {
    val nameStartChar = """:A-Z_a-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u02FF\u0370-\u037D\u037F-\u1FFF\u200C-\u200D\u2070-\u218F\u2C00-\u2FEF\u3001-\uD7FF\uF900-\uFDCF\uFDF0-\uFFFD\x{10000}-\x{EFFFF}"""
    val pattern = """[%s][%s\-\.0-9\u00B7\u0300-\u036F\u203F-\u2040]*""".format(nameStartChar, nameStartChar)
    pattern.r
  }

  def apply(name: String): Elem = apply(name, Attributes())

  def apply(name: String, attrs: Attributes): Elem = apply(name, attrs, Group.empty)

  def apply(name: String, attrs: Attributes, children: Group[Node]): Elem = apply(None, name, attrs, NamespaceBinding.empty, children)

  def apply(nb: NamespaceBinding, name: String): Elem = apply(nb, name, Attributes())

  def apply(nb: NamespaceBinding, name: String, attrs: Attributes): Elem = apply(nb, name, attrs, Group.empty)

  def apply(nb: NamespaceBinding, name: String, attrs: Attributes, children: Group[Node]): Elem = {
    Elem(nb.headOption.flatMap(_.prefix), name, attrs, nb, children)
  }

  def validateNamespace(elem: Elem, ns: String) = {
    val binding = elem.namespaces.findByPrefix(elem.prefix.getOrElse(""))
    binding.filter(_.uri == Some(ns)).isDefined
  }

  private[antixml] def isValidName(string: String) = NameRegex.pattern.matcher(string).matches

  private[antixml] def isValidNamespaceUri(uri: String) = uri.trim().length() > 0

  def validateAttributes(attrs: Attributes, namespaces: NamespaceBinding) {
    attrs.foreach {
      case (QName(Some(prefix), name), value) =>
        // "xml:" prefix is always valid for XML, see http://www.w3.org/XML/1998/namespace
        if (prefix != "xml" && !namespaces.findByPrefix(prefix).isDefined)
          throw new IllegalArgumentException("Attribute with name '%s' with prefix '%s' is not defined on element".format(name, prefix)
          )
      case _ =>
    }
  }
}


/**
  * A node containing a single string, representing character data in the XML tree.
  * For example:
  *
  * {{{
  * Lorem ipsum &amp; dolor sit amet
  * }}}
  *
  * This would result in the following node:
  *
  * {{{
  * Text("Lorem ipsum & dolor sit amet")
  * }}}
  *
  * Note that reserved characters (as defined by the XML 1.0 spec) are escaped
  * when calling `toString`.  Thus, if you invoke `toString` on the `Text` node
  * given in the example above, the result will reverse back into the original
  * text, including the `&amp;` escape.  If you need a text representation which
  * does ''not'' escape characters on output, use [[com.codecommit.antixml.CDATA]].
  */
case class Text(text: String) extends Node {
  if (!Node.hasOnlyValidChars(text))
    throw new IllegalArgumentException("Illegal character in text '" + text + "'")

  override def toString = Node.escapeText(text)
}

/**
  * A node containing a single string, representing unescaped character data in
  * the XML tree.  For example:
  *
  * {{{
  * <![CDATA[Lorem ipsum & dolor sit amet]]>
  * }}}
  *
  * This would result in the following node:
  *
  * {{{
  * CDATA("Lorem ipsum & dolor sit amet")
  * }}}
  *
  * Note that reserved characters (as defined by the XML 1.0 spec) are ''not''
  * escaped when calling `toString`.  If you need a text representation which
  * performs escaping, use [[com.codecommit.antixml.Text]]
  */
case class CDATA(text: String) extends Node {
  if (text.contains("]]>"))
    throw new IllegalArgumentException("CDATA nodes can't contain ']]>'")

  if (!Node.hasOnlyValidChars(text))
    throw new IllegalArgumentException("Illegal character in CDATA '" + text + "'")

  override def toString = "<![CDATA[" + text + "]]>"
}

/**
  * A node representing an entity reference. For example:
  *
  * {{{
  * &hellip;
  * }}}
  *
  * This would result in the following node:
  *
  * {{{
  * EntityRef("hellip")
  * }}}
  */
case class EntityRef(entity: String) extends Node {
  if (!Node.hasOnlyValidChars(entity))
    throw new IllegalArgumentException("Illegal character in EntityRef '" + entity + "'")

  override def toString = "&" + entity + ";"
}
