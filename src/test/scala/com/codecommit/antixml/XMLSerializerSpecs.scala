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
import java.io.StringWriter

class XMLSerializerSpecs extends Specification {
  import XML._
  
  "xml serialization" should {
    "not redeclare undeclared NSes" in {
      fromString("<a xmlns='urn:x'><b/></a>").toString mustEqual """<a xmlns="urn:x"><b/></a>"""
    }

    "serialize elements with multiple unprefixed bindings correctly" in {
      val x = """<a xmlns="urn:x"><b xmlns="urn:y"/></a>"""
      Elem(None, "a", Attributes(), NamespaceBinding("urn:x"), Group(
        Elem(None, "b", Attributes(), NamespaceBinding("urn:y"), Group()))).toString mustEqual x
    }

    "not be fooled by unprefixed namespace bindings" in {
      val x = """<a xmlns="urn:x"><b xmlns="urn:y"/></a>"""
      Elem(None, "a", Attributes(), NamespaceBinding("urn:x"), Group(
        Elem(None, "b", Attributes(), NamespaceBinding("urn:y"), Group()))).toString mustEqual x
    }

    "not redeclared unnecessary re-reclared namespaces" in {
      // The xmlns declaration on the 'b' element is not required and it optimized.
      fromString("<a xmlns='urn:x'><b xmlns='urn:x'/></a>").toString mustEqual """<a xmlns="urn:x"><b/></a>"""
    }

    "declared redefined default namespaces namespaces" in {
      fromString("<a xmlns='urn:x'><b xmlns='urn:y'/><b xmlns='urn:y'/></a>").toString mustEqual """<a xmlns="urn:x"><b xmlns="urn:y"/><b xmlns="urn:y"/></a>"""
    }

    "serialize prefixes minimally" in {
      fromString("<my:test xmlns:my='urn:my-urn:quux'>\n<beef/>\n\t\n</my:test>").toString mustEqual "<my:test xmlns:my=\"urn:my-urn:quux\">\n<beef/>\n\t\n</my:test>"
    }

    "serialize unprefixed elements correctly" in {
      fromString("<test xmlns='urn:my-urn:quux'>\n<beef/>\n\t\n</test>").toString mustEqual "<test xmlns=\"urn:my-urn:quux\">\n<beef/>\n\t\n</test>"
    }

    "serialize elements with multiple prefixes correctly" in {
      val x = """<a xmlns="urn:x"><b xmlns="urn:y"/><b xmlns="urn:y"/></a>"""
      fromString(x).toString mustEqual x
    }

    /**
     * This covers the use case where you know/suspect elements to use extra namespaces, like when Atom entries
     * use Atom extensions.
     */
    "serialize pre-emptively added namespace correctly" in {
      val main = NamespaceBinding("urn:main")
      val ext = NamespaceBinding("ext", "urn:ext", main)
      val x = Elem(None, "feed", Attributes(), ext, Group(
        Elem(Some("ext"), "my-ext", Attributes(), ext, Group()),
        Elem(Some("ext"), "my-ext", Attributes(), ext, Group())
      ))

      x.toString mustEqual """<feed xmlns="urn:main" xmlns:ext="urn:ext"><ext:my-ext/><ext:my-ext/></feed>"""
    }

    /**
     * This covers the use case where you know/suspect elements to use extra namespaces, like when Atom entries
     * use Atom extensions.
     */
    "serialize parsed xml then reparse" in {
      val xml = XML.fromInputStream(getClass.getResourceAsStream("/jira-rss-derby-project.xml"))
      val writer = new StringWriter()
      xml.writeTo(writer)
      val xml2 = XML.fromString(writer.toString)
      xml2 shouldEqual(xml)
    }
  }
}
