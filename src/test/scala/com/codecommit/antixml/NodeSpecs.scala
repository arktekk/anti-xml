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

import org.specs2.mutable._
import org.specs2.matcher.DataTables
import org.specs2.ScalaCheck
import org.scalacheck._

class NodeSpecs extends Specification with DataTables with ScalaCheck with XMLGenerators with LowPrioritiyImplicits {
  import Prop._
  
  lazy val numProcessors = Runtime.getRuntime.availableProcessors()
  implicit val params = set(workers = numProcessors, maxSize = 15)      // doesn't need to be so large
  
  val nameStartChar = """:A-Z_a-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u02FF\u0370-\u037D\u037F-\u1FFF\u200C-\u200D\u2070-\u218F\u2C00-\u2FEF\u3001-\uD7FF\uF900-\uFDCF\uFDF0-\uFFFD\x{10000}-\x{EFFFF}"""
  val name = "[" + nameStartChar + "][" + nameStartChar + """\-\.0-9\u00B7\u0300-\u036F\u203F-\u2040]*"""r
  val textReg = """[\u0009\u000A\u000D\u0020-\uD7FF\uE000-\uFFFD\x{10000}-\x{10FFFF}]*""".r
  
  "elements" should {
    "serialize empty elements correctly" in {
      <br/>.convert.toString mustEqual "<br/>"
    }

    "serialize empty elements with namespace correctly" in {
      <br xmlns="urn:quux"/>.convert.toString mustEqual "<br xmlns=\"urn:quux\"/>"
    }

    "escape reserved characters in attribute values" in {
      "character" || "elem.toString"         |>
      "\""        !! "<foo bar='\"'/>"       |
      "&"         !! "<foo bar=\"&amp;\"/>"  |
      "'"         !! "<foo bar=\"'\"/>"      |
      "'\"'"      !! "<foo bar='&apos;\"&apos;'/>" |
      "<"         !! "<foo bar=\"&lt;\"/>"   | { (c, r) => Elem(None, "foo", Attributes("bar" -> c), NamespaceBinding.empty, Group()).toString mustEqual r }
    }
    
    "allow legal name identifiers" in {
        Elem(None, "bar", Attributes(), NamespaceBinding.empty, Group()) must not(throwAn[IllegalArgumentException])
    }
    
    "detect illegal name identifiers" in prop { str: String =>
      name unapplySeq str match {
        case Some(_) => Elem(None, str, Attributes(), NamespaceBinding.empty, Group()) must not(throwAn[IllegalArgumentException])
        case None => Elem(None, str, Attributes(), NamespaceBinding.empty, Group()) must throwAn[IllegalArgumentException]
      }
    }
    
    "allow legal prefix identifiers" in {
        Elem(Some("foo"), "foo", Attributes(), NamespaceBinding.empty, Group()) must not(throwAn[IllegalArgumentException])
    }
    
    "detect illegal prefix identifiers" in prop { str: String =>
      name unapplySeq str match {
        case Some(_) => Elem(Some(str), "foo", Attributes(), NamespaceBinding(str, "urn:a"), Group()) must not(throwAn[IllegalArgumentException])
        case None => Elem(Some(str), "foo", Attributes(), NamespaceBinding(str, "urn:a"), Group()) must throwAn[IllegalArgumentException]
      }
    }
    
    "allow legal attribute prefixes" in {
        Elem(None, "foo", Attributes(QName("foo", "bar") -> "bar"), NamespaceBinding("foo", "urn:a"), Group()) must not(throwAn[IllegalArgumentException])
    }
    
    "add children conveniently" in {
      val elem = Elem(None, "foo", Attributes(), NamespaceBinding.empty, Group())
      val withChildren = elem.addChildren(Group(Elem(None, "bar"), Text("Hello")))
      withChildren.children.size must beEqualTo(2)
    }

    "replace children conveniently" in {
      val elem = Elem(None, "foo", Attributes(), NamespaceBinding.empty, Group(Text("Somewhat crazy")))
      val withChildren = elem.withChildren(Group.empty)
      withChildren.children must beEmpty
    }

    "add namespace conveniently" in {
      val elem = Elem(None, "foo", Attributes(), NamespaceBinding("urn:foo:bar"), Group(Text("Somewhat crazy")))
      val withChildren = elem.addNamespace("bar", "urn:foo:baz")
      withChildren.namespaces.toList must beEqualTo(List(NamespaceEntry("bar", "urn:foo:baz"), NamespaceEntry("urn:foo:bar")))
    }

    "generate namespace" in {
      val elem = Elem(None, "foo", Attributes(), NamespaceBinding("urn:foo:bar"), Group(Text("Somewhat crazy")))
      val withChildren = elem.addNamespace("", "urn:foo:baz")
      withChildren.namespaces.toList must beEqualTo(List(NamespaceEntry("ns1", "urn:foo:baz"), NamespaceEntry("urn:foo:bar")))
    }

    "detect illegal attribute prefixes" in prop { str: String =>
      name unapplySeq str match {
        case Some(_) => Elem(None, "foo", Attributes(QName(str, "bar") -> "bar"), NamespaceBinding(str, "urn:bar"), Group()) must not(throwAn[IllegalArgumentException])
        case None => Elem(None, "foo", Attributes(QName(str, "bar") -> "bar"), NamespaceBinding.empty, Group()) must throwAn[IllegalArgumentException]
      }
    }
    
    "allow legal attribute names" in {
        Elem(None, "foo", Attributes("foo" -> "bar"), NamespaceBinding.empty, Group()) must not(throwAn[IllegalArgumentException])
    }

    "allow attribute removal" in {
      Elem(None, "foo", Attributes("foo" -> "bar"), NamespaceBinding.empty, Group()).removeAttribute("foo") must beEqualTo(Elem(None, "foo", Attributes(), NamespaceBinding.empty, Group()))
    }

    "allow attributes removal" in {
      Elem(None, "foo", Attributes(("foo","bar"), ("a","b")), NamespaceBinding.empty, Group()).removeAttributes(Seq("foo","a")) must beEqualTo(Elem(None, "foo", Attributes(), NamespaceBinding.empty, Group()))
    }

    "allow attribute modification" in {
      Elem(None, "foo", Attributes(("foo","bar"), ("a","b")), NamespaceBinding.empty, Group()).modifyAttributes(Attributes(("foo", "newBar"),("a","newB"))) must beEqualTo(Elem(None, "foo", Attributes(("foo","newBar"), ("a","newB")), NamespaceBinding.empty, Group()))
    }

    "detect illegal attribute names" in prop { str: String =>
      name unapplySeq str match {
        case Some(_) => Elem(None, "foo", Attributes(str -> "bar"), NamespaceBinding.empty, Group()) must not(throwAn[IllegalArgumentException])
        case None => Elem(None, "foo", Attributes(str -> "bar"), NamespaceBinding.empty, Group()) must throwAn[IllegalArgumentException]
      }
    }

    "select against self" in {
      val bookstore = <bookstore><book><title>For Whom the Bell Tolls</title><author>Hemmingway</author></book><book><title>I, Robot</title><author>Isaac Asimov</author></book><book><title>Programming Scala</title><author>Dean Wampler</author><author>Alex Payne</author></book></bookstore>.convert
      (bookstore \ "book") mustEqual bookstore.children
      (bookstore \ "book") mustEqual bookstore.children
      (bookstore \\ "title") mustEqual (bookstore.children \\ "title")
    }
    
    "select text within self" in {
      (<parent>Text</parent>.convert \\ text mkString) mustEqual "Text"
    }
    
    "delegate canonicalization to Group" in prop { e: Elem =>
      e.canonicalize mustEqual e.copy(children=e.children.canonicalize)
    }
  }
  
  "text nodes" should {
    "escape reserved characters when serialized" in {
      Text("Lorem \" ipsum & dolor ' sit < amet > blargh").toString mustEqual "Lorem \" ipsum &amp; dolor ' sit &lt; amet &gt; blargh"
    }
    "Support large texts without overflowing" in {
      val text = io.Source.fromInputStream(getClass.getResourceAsStream("/lots-of-text.txt")).getLines().mkString("\n")
      Text(text).toString mustEqual Node.escapeText(text)
    }
    "disallow illegal characters" in prop {str: String =>
      textReg unapplySeq str match {
        case Some(_) => Text(str) must not(throwAn[IllegalArgumentException])
        case None => Text(str) must throwAn[IllegalArgumentException]
      }
    }
    "allow characters greater than U+FFFF" in {
      Text(new java.lang.StringBuilder().appendCodePoint(0x10000).toString) must not(throwAn[IllegalArgumentException])
    }

  }
  
  "cdata nodes" should {
    "not escape reserved characters when serialized" in {
      CDATA("Lorem \" ipsum & dolor ' sit < amet > blargh").toString mustEqual "<![CDATA[Lorem \" ipsum & dolor ' sit < amet > blargh]]>"
    }
    "Reject the ]]> string in the constructor" in {
      CDATA("la di ]]> da") must throwAn[IllegalArgumentException]
    }
    "Support large texts without overflowing" in {
      val text = io.Source.fromInputStream(getClass.getResourceAsStream("/lots-of-text.txt")).getLines().mkString("\n")
      CDATA(text).toString mustEqual "<![CDATA[%s]]>".format(text)
    }

  }

}
