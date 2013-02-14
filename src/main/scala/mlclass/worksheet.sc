package mlclass
import scalaz._
import Scalaz._

object worksheet {
  println("Welcome to the Scala worksheet")       //> Welcome to the Scala worksheet
  val a = Map("a" -> 1, "b" -> 2, "c" -> 3)       //> a  : scala.collection.immutable.Map[java.lang.String,Int] = Map(a -> 1, b ->
                                                  //|  2, c -> 3)
  val b = Map("b" -> 5, "d" -> 8)                 //> b  : scala.collection.immutable.Map[java.lang.String,Int] = Map(b -> 5, d ->
                                                  //|  8)
  val c = a |+| b                                 //> c  : scala.collection.immutable.Map[java.lang.String,Int] = Map(a -> 1, b ->
                                                  //|  7, c -> 3, d -> 8)
  
  val d = a |+| Map("b" -> 3)                     //> d  : scala.collection.immutable.Map[java.lang.String,Int] = Map(a -> 1, b ->
                                                  //|  5, c -> 3)
  
  "hello".groupBy(identity).mapValues(_.length)   //> res0: scala.collection.immutable.Map[Char,Int] = Map(h -> 1, e -> 1, o -> 1,
                                                  //|  l -> 2)
                                                  
  1 to 10                                         //> res1: scala.collection.immutable.Range.Inclusive = Range(1, 2, 3, 4, 5, 6, 7
                                                  //| , 8, 9, 10)
}