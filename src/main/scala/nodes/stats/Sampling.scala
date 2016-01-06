package nodes.stats

import breeze.linalg.{DenseVector, DenseMatrix}
import lineage._
import org.apache.spark.rdd.RDD
import pipelines.FunctionNode
import workflow.Transformer

/**
 * Given a collection of Dense Matrices, this will generate a sample of
 * @param numSamplesPerMatrix columns from each matrix.
 */
case class ColumnSampler(numSamplesPerMatrix: Int)
  extends Transformer[DenseMatrix[Float], DenseMatrix[Float]] {

  def apply(in: DenseMatrix[Float]): DenseMatrix[Float] = {
    val cols = Seq.fill(numSamplesPerMatrix) {
      scala.util.Random.nextInt(in.cols)
    }
    in(::, cols).toDenseMatrix
  }

  override def saveLineageAndApply(in: RDD[DenseMatrix[Float]], tag: String): RDD[DenseMatrix[Float]] = {
    val outRDD = in.map{ m =>
      val cols = Seq.fill(numSamplesPerMatrix) {
        scala.util.Random.nextInt(m.cols)
      }
      (m(::, cols).toDenseMatrix, cols, m.rows)
    }
    outRDD.cache()
    val out = outRDD.map(x => x._1)

    val indexRDD = outRDD.map(x => (x._2,x._3))
    val ioListRDD = indexRDD.map{
      case (seq, rows) => {
        seq.toList.zipWithIndex.map(
          t => (Shape((0d,t._1.toDouble), ((rows-1).toDouble,t._1.toDouble)), Shape((0d,t._2.toDouble), ((rows-1).toDouble,t._2.toDouble)))
        )
      }
    }

    val lineage = GeoLineage(in, out, ioListRDD, this)
    //lineage.save(tag)
    println("collecting lineage for Transformer "+this.label+"\t mapping: "+lineage.qBackward(List(Coor(0,0,0))))
    out
  }

}


/**
 * Takes a sample of an input RDD of size size.
 * @param size Number of elements to return.
 */
class Sampler[T](val size: Int, val seed: Int = 42) extends FunctionNode[RDD[T], Array[T]] {
  def apply(in: RDD[T]): Array[T] = {
    in.takeSample(false, size, seed)
  }
}
