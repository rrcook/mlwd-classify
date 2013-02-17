A transation of Hilary Mason's classifier from her video class, 
"Machine Learning with Web Data".

Classify.scala is a straight port to Scala.
I used the Chalk library where Hilary's original code used nltk, but
beyond that I tried to follow the code to see if Scala was faster
than Python; it was in my runs.

ClassifyAkka.scala is an attempt to break out parts that could
be parallelized. The code to turn a line into features is functional
and standalone. In this version I'm having trouble with incrementFeature
and incrementCat. I hope to be able to make these methods more functional
after I read "Functional Programming in Scala" and put more into the 
workers.


