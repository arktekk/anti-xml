package com.codecommit.antixml

import scala.language.implicitConversions

/**
 * Allow these to be mixed in where needed, instead of having to import the package object.
 */
trait LowPrioritiyImplicits {
  /**
     * Pimps the `convert` method onto any object for which there exists a conversion
     * into Anti-XML.  Note that this conversion is an implicit value, statically
     * enforced and thus shouldn't be the source of any collision issues.  It should
     * actually be possible to have another implicit conversion in scope which
     * pimps the `convert` method without seeing conflicts.
     *
     * @see [[com.codecommit.antixml.XMLConvertable]]
     */
    implicit def nodeSeqToConverter[A](a: A): Converter[A] = new Converter(a)

    // I feel justified in this global implicit since it doesn't pimp anything
    implicit def stringTupleToQNameTuple(pair: (String, String)): (QName, String) = {
      val (key, value) = pair
      (QName(None, key), value)
    }

    implicit def stringToNsRepr(s: String) = NSRepr(s)

    implicit def namespaceBindingToNsRepr(nb: NamespaceEntry) = NSRepr(nb)

    /**
     * Wildcard selector which passes ''all'' nodes unmodified.  This is analogous
     * to the `"_"` selector syntax in `scala.xml`.  For example: `ns \ * \ "name"`
     */
    val `*`: Selector[Node] = Selector({ case n: Node => n })

    /**
     * Non-node selector which finds exclusively [[com.codecommit.antixml.Text]]
     * nodes and pulls out their `String` content.  Unlike most selectors, the
     * result of using this selector is not a [[com.codecommit.antixml.Group]], but
     * a generic [[scala.collection.Traversable]]`[String]`.  This selector can
     * be used to emulate the `NodeSeq#text` method provided by `scala.xml`.  For
     * example: `ns \\ text mkString` (this is analogous, but not quite equivalent
     * to calling `ns.text` in `scala.xml`).
     */
    val text: Selector[String] = Selector({ case Text(str) => str })
}
