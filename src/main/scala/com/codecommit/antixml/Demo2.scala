package com.codecommit.antixml

object Demo2 extends App {

  val order =
    <Order>
      <OrderLines>
        <OrderLine ItemID="0"/>
        <OrderLine ItemID="1"/>
      </OrderLines>
    </Order>.convert

  val x = (order \ "OrderLines" \ "OrderLine").map(_.copy(name = "Pippo")).unselect.unselect

  println(x)

}
