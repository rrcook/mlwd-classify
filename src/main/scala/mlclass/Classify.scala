package mlclass

import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import chalk.tools.tokenize.TokenizerME
import chalk.tools.tokenize.TokenizerModel
import org.tartarus.snowball.ext.EnglishStemmer
import scala.io.Source
import scala.collection.mutable.HashMap

object Classify extends App {

  val labels = List("arts", "sports")

  val stemmer = new EnglishStemmer()

  val PUNCTUATION = """[!"#$%&'()*+,-./:;<=>?@\[\]^_`{|}~]""".r

  var modelIn: InputStream = new FileInputStream("models/en-token.bin");
  var model: TokenizerModel = null

  val featureCount = new HashMap[String, HashMap[String, Int]]
  val categoryCount = new HashMap[String, Int]
  val blankCategory = new HashMap[String, Int]

  try {
    model = new TokenizerModel(modelIn);
  } catch {
    case e: IOException => {
      e.printStackTrace()
      modelIn = null
    }
  } finally {
    if (modelIn != null) modelIn.close()
  }
  val tokenizer = new TokenizerME(model)

  def escapePunctuation(text: String): String = PUNCTUATION.replaceAllIn(text, "")

  def stem(text: String) = {
    stemmer.setCurrent(text)
    stemmer.stem()
    stemmer getCurrent
  }

  def correctLength(s: String) = {
    val len = s.length
    len > 3 && len < 16
  }

  // Standard all over the scala blogs and questions
  def frequencies[T](seq: Seq[T]) = seq.groupBy(identity).mapValues(_.length)

  def probability(item: String, category: String) = {
    val categoryProb = getCategoryCount(category) / categoryCount.values.sum
    documentProbability(item, category) * categoryProb
  }

  def documentProbability(item: String, category: String) = {
    getFeatures(item).keys.map(weightedProb(_, category)).foldLeft(1.0)(_ * _)
  }

  def getFeatures(text: String) = {
    frequencies(tokenizer.tokenize(escapePunctuation(text).toLowerCase()) filter { correctLength _ } map { stem _ })
  }

  def getFeatureCount(feature: String, category: String) = featureCount.getOrElse(feature, blankCategory).getOrElse(category, 0).toDouble

  def getCategoryCount(category: String) = categoryCount.getOrElse(category, 0).toDouble

  def featureProb(f: String, category: String) = { // Pr(A|B)
    val categoryCount = getCategoryCount(category)
    if (categoryCount == 0.0) 0.0 else getFeatureCount(f, category) / categoryCount
  }

  def weightedProb(f: String, category: String, weight: Double = 1.0, ap: Double = 0.5) = {
    val basicProb = featureProb(f, category)
    val totals = categoryCount.keys.map(getFeatureCount(f, _)).sum
    // println("totals = " + totals)
    val wp = ((weight * ap) + (totals * basicProb)) / (weight + totals)
    // println(f + ", " + category + ", " + wp)
    wp
  }

  def incrementFeature(feature: String, category: String): Unit = {
    val catMap = featureCount.getOrElse(feature, new HashMap[String, Int])
    featureCount(feature) = catMap
    catMap(category) = catMap.getOrElse(category, 0) + 1
  }

  def incrementCat(category: String): Unit = {
    categoryCount(category) = categoryCount.getOrElse(category, 0) + 1
  }

  def train(item: String, category: String): Unit = {
    for ((f, v) <- getFeatures(item)) incrementFeature(f, category)
    incrementCat(category)
  }

  def trainFromData(data: Map[String, Array[String]]): Unit = {
    val tStart = System.currentTimeMillis()
    for ((category, documents) <- data; doc <- documents) train(doc, category)
    val tEnd = System.currentTimeMillis()
    System.out.println("Training time = " + (tEnd - tStart))
  }

  def readLines(fileName: String) = {
    val source = Source.fromFile("data/" + fileName)
    val lines = source.getLines.toArray
    source.close
    lines
  }


  val start: Long = System.currentTimeMillis()
  val data = labels.zip(labels.map(readLines)).toMap
  trainFromData(data)

  // println(data("arts").mkString(", "))

  println(probability("Early Friday afternoon, the lead negotiators for the N.B.A. and the players union will hold a bargaining session in Beverly Hills - the latest attempt to break a 12-month stalemate on a new labor deal.", "arts"))
  println(probability("Early Friday afternoon, the lead negotiators for the N.B.A. and the players union will hold a bargaining session in Beverly Hills - the latest attempt to break a 12-month stalemate on a new labor deal.", "sports"))

  val end = System.currentTimeMillis()
  println("We're Done! ", end - start)
}