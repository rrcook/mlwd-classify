package mlclass

import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import scala.Array.canBuildFrom
import scala.collection.mutable.HashMap
import scala.io.Source
import org.tartarus.snowball.ext.EnglishStemmer
import akka.actor.Actor
import chalk.tools.tokenize.TokenizerME
import chalk.tools.tokenize.TokenizerModel
import akka.actor.ActorRef
import akka.actor.Props
import akka.routing.RoundRobinRouter
import akka.actor.ActorSystem

object ClassifyAkka extends App {
  
  case class Work(category: String, text: String)
  case class WorkResult(category: String, features: Map[String, Int])
  case object Train
  case class TrainResult(featureCount: HashMap[String, HashMap[String, Int]], categoryCount: HashMap[String, Int], start: Long)

  val labels = List("arts", "sports")

  val PUNCTUATION = """[!"#$%&'()*+,-./:;<=>?@\[\]^_`{|}~]""".r

  var modelIn: InputStream = new FileInputStream("models/en-token.bin");
  var model: TokenizerModel = null

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

  def escapePunctuation(text: String): String = PUNCTUATION.replaceAllIn(text, "")

  def stem(text: String, stemmer: EnglishStemmer) = {
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

  def getFeatures(tokenizer: TokenizerME, stemmer: EnglishStemmer, text: String) = {
    frequencies(tokenizer.tokenize(escapePunctuation(text).toLowerCase()) filter { correctLength _ } map { stem(_, stemmer) })
  }

  class Worker extends Actor {
    
    // I'm unsure of the parallel/thread safety of these objects
    val tokenizer = new TokenizerME(model)
    val stemmer = new EnglishStemmer

    def receive = {
      case Work(category, text) =>
        sender ! WorkResult(category, getFeatures(tokenizer, stemmer, text)) // perform the work
    }
  }

  class Trainer(nrOfWorkers: Int,
    categories: List[String],
    listener: ActorRef) extends Actor {
    
    val start: Long = System.currentTimeMillis

    val stemmer = new EnglishStemmer()
    val tokenizer = new TokenizerME(model)

    val featureCount = new HashMap[String, HashMap[String, Int]]
    val categoryCount = new HashMap[String, Int]

    var linesRead: Int = 0
    var linesProcessed: Int = 0
    var readAll: Boolean = false
    
    var tStart: Long = 0L
    var tEnd: Long = 0L


    def incrementFeature(feature: String, category: String): Unit = {
      val catMap = featureCount.getOrElse(feature, new HashMap[String, Int])
      featureCount(feature) = catMap
      catMap(category) = catMap.getOrElse(category, 0) + 1
    }

    def incrementCat(category: String): Unit = {
      categoryCount(category) = categoryCount.getOrElse(category, 0) + 1
    }

    val workerRouter = context.actorOf(
      Props[Worker].withRouter(RoundRobinRouter(nrOfWorkers)), name = "workerRouter")

    def receive = {
      case Train =>
        tStart = System.currentTimeMillis()
        for (category <- categories) {
          val source = Source.fromFile("data/" + category)
          val lines = source.getLines.toArray
          linesRead += lines.length
          for (line <- lines) {
            workerRouter ! Work(category, line)
          }
          source close
        }
        readAll = true

      case WorkResult(category, features) =>
        for ((f, v) <- features) incrementFeature(f, category)
        incrementCat(category)
        linesProcessed += 1
        if (linesRead == linesProcessed && readAll) {
          tEnd = System.currentTimeMillis()
          System.out.println("Training time = " + (tEnd - tStart))
          listener ! TrainResult(featureCount, categoryCount, start)
          context.stop(self)
        }
    }

  }

  class Listener extends Actor {
    val tokenizer = new TokenizerME(model)
    val stemmer = new EnglishStemmer

    var featureCount: HashMap[String, HashMap[String, Int]] = _
    var categoryCount: HashMap[String, Int] = _

    def probability(item: String, category: String) = {
      val categoryProb = getCategoryCount(category) / categoryCount.values.sum
      documentProbability(item, category) * categoryProb
    }

    def documentProbability(item: String, category: String) = {
      getFeatures(tokenizer, stemmer, item).keys.map(weightedProb(_, category)).foldLeft(1.0)(_ * _)
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

    // println(data("arts").mkString(", "))

    val end: Long = System.currentTimeMillis()
    def receive = {
      case TrainResult(fc, cc, start) =>
        featureCount = fc
        categoryCount = cc

        println(probability("Early Friday afternoon, the lead negotiators for the N.B.A. and the players union will hold a bargaining session in Beverly Hills - the latest attempt to break a 12-month stalemate on a new labor deal.", "arts"))
        println(probability("Early Friday afternoon, the lead negotiators for the N.B.A. and the players union will hold a bargaining session in Beverly Hills - the latest attempt to break a 12-month stalemate on a new labor deal.", "sports"))

        val end = System.currentTimeMillis()
        println("We're Done! ",  end - start)

        context.system.shutdown()
    }
  }

  def classify(nrOfWorkers: Int) {
    // Create an Akka system
    val system = ActorSystem("ClassifySystem")

    // create the result listener, which will print the result and 
    // shutdown the system
    val listener = system.actorOf(Props[Listener], name = "listener")

    // create the trainer
    val trainer = system.actorOf(Props(new Trainer(
      nrOfWorkers, labels, listener)),
      name = "trainer")

    // start the chain of processes
    trainer ! Train

  }

  classify(nrOfWorkers = 2)

}