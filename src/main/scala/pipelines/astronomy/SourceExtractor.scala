package pipelines.astronomy

import breeze.linalg._
import loaders.FitsLoader
import nodes.util.MaxClassifier
import org.apache.commons.math3.random.MersenneTwister
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import pipelines.Logging
import workflow._
import scopt.OptionParser

object BkgSubstract extends Transformer[DenseMatrix[Double], DenseMatrix[Double]] {
  def apply(in: DenseMatrix[Double]): DenseMatrix[Double] = {
    var matrix = Array.ofDim[Double](in.rows, in.cols)
    for(i <- 0 until in.rows; j <- 0 until in.cols)
      matrix(i)(j) = in(i,j)

    var bkg = new Background(matrix)
    val newMatrix = bkg.subfrom(matrix)

    var stream = Array.ofDim[Double](in.rows * in.cols)
    (0 until in.rows).map(i => {
      Array.copy(newMatrix(i), 0, stream, i*in.cols, in.cols)
    })
    new DenseMatrix(in.rows, in.cols, stream)
  }
}

object Extract extends Transformer[DenseMatrix[Double], DenseMatrix[Double]] {
  def apply(in: DenseMatrix[Double]): DenseMatrix[Double] = {
    /*hard-coded rms results in more objects detected, this is temporary
     *solution to make intermediate data solely as dense matrix
     */
    val rms = 4.7
    val ex = new Extractor
    var matrix = Array.ofDim[Double](in.rows, in.cols)
    for(i <- 0 until in.rows; j <- 0 until in.cols)
      matrix(i)(j) = in(i,j)

    val objects = ex.extract(matrix, (1.5 * rms).toFloat)
    val array = objects.map(_.toDoubleArray)

    val rows = objects.size
    val cols = 28
    var stream = Array.ofDim[Double](rows*cols)
    (0 until rows).map(i => {
      Array.copy(array(i), 0, stream, i*cols, cols)
    })
    new DenseMatrix(rows, cols, stream)
  }
}

object Counter extends Transformer[DenseMatrix[Double], Int] {
  def apply(in: DenseMatrix[Double]): Int = in.rows
}

object SourceExtractor extends Serializable with Logging {
  val appName = "SourceExtractor"

  def run(sc: SparkContext, conf: SourceExtractorConfig) {
    val pipeline = BkgSubstract andThen Extract andThen Counter
    val startTime = System.nanoTime()
    val count = pipeline(FitsLoader(sc, conf.inputPath)).reduce(_ + _)
    logInfo(s"Detected ${count} objects")
    val endTime = System.nanoTime()
    logInfo(s"Pipeline took ${(endTime - startTime)/1e9} s")
  }

  case class SourceExtractorConfig(
      inputPath: String = ""
  )

  def parse(args: Array[String]): SourceExtractorConfig = new OptionParser[SourceExtractorConfig](appName) {
    head(appName, "0.1")
    help("help") text("prints this usage text")
    opt[String]("inputPath") required() action { (x,c) => c.copy(inputPath=x) }
  }.parse(args, SourceExtractorConfig()).get

  /**
   * The actual driver receives its configuration parameters from spark-submit usually.
   * @param args
   */
  def main(args: Array[String]) = {
    val appConfig = parse(args)

    val conf = new SparkConf().setAppName(appName)
    conf.setIfMissing("spark.master", "local[1]")
    val sc = new SparkContext(conf)
    run(sc, appConfig)

    sc.stop()
  }
}