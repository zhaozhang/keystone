package workflow

import breeze.linalg._
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import utils.{MultiLabeledImage, Image, LabeledImage, ImageMetadata}

import java.io._
import scala.collection.mutable.Map
import scala.reflect.ClassTag
import scala.io.Source

/**
 *  Each Lineage corresponds to one transformer
 *  @param inRDD input RDD of the transformer
 *  @param outRDD output RDD of the transformer
 *  @param mappingRDD each item of mappingRDD corresponds to one item in inRDD and one item in outRDD
 *  @param modelRDD related models of transformer, it can be a random seed, a vector or a matrix
 *  @param transformer the transformer itself
 */

class KeystoneLineage(inRDD: RDD[_], outRDD: RDD[_], mappingRDD: RDD[_], transformer: Transformer[_,_], 
	modelRDD: Option[_]) extends serializable{

	//qForward() and qBackward() methods need implementation, should call to mappingRDD
	def qForward(key: Option[_]) = List((1, 1))
	def qBackward(key: Option[_]) = List((1, 1))

  def save(tag: String) = {
  	//need to save RDD in each directory
  	val path = "Lineage"
  	val context = mappingRDD.context
  	val rdd = context.parallelize(Seq(transformer), 1)
  	rdd.saveAsObjectFile(path+"/"+tag+"/transformer")
  	mappingRDD.saveAsObjectFile(path+"/"+tag+"/mappingRDD")
  }
}

object KeystoneLineage{
	implicit def intToOption(key: Int): Option[Int] = Some(key)
	implicit def int2DToOption(key: (Int, Int)): Option[(Int, Int)] = Some(key)
	implicit def indexInt2DToOption(key: (Int, (Int, Int))): Option[(Int, (Int, Int))] = Some(key)
}

object OneToOneKLineage{
	def apply(in: RDD[_], out:RDD[_], transformer: Transformer[_, _], model: Option[_] = None) = {
		val mapping = in.zip(out).map({
			case (vIn: DenseVector[_], vOut: DenseVector[_]) => {
				new OneToOneMapping(vIn.size, 1, vOut.size, 1, 1, List(in.id), List(out.id))
			}
			case (sIn: Seq[_], vOut: DenseVector[_]) => {
				//sIn should be Seq[DenseVetor[_]]
				val sampleInVector = sIn(0).asInstanceOf[DenseVector[_]]
				new OneToOneMapping(sampleInVector.size, 1, vOut.size, 1, sIn.size, List(in.id), List(out.id))
			}
			case (mIn: DenseMatrix[_], mOut: DenseMatrix[_]) => {
				new OneToOneMapping(mIn.rows, mIn.cols, mOut.rows, mOut.cols, 1, List(in.id), List(out.id))
			}
			case (mIn: DenseMatrix[_], mOut: DenseVector[_]) => {
				new OneToOneMapping(mIn.rows, mIn.cols, mOut.size, 1, 1, List(in.id), List(out.id))
			}
			case (imageIn: MultiLabeledImage, imageOut: Image) => {
				new OneToOneMapping(imageIn.image.flatSize, 1, imageOut.flatSize, 1, 1, List(in.id), List(out.id), Some(imageOut.metadata))
			}
			case (imageIn: Image, imageOut: Image) => {
				new OneToOneMapping(imageIn.flatSize, 1, imageOut.flatSize, 1, 1, List(in.id), List(out.id), Some(imageOut.metadata))
			}
			case _ => None
		})
		new KeystoneLineage(in, out, mapping, transformer, model)
	}
}

object AllToOneKLineage{
	def apply(in: RDD[_], out:RDD[_], transformer: Transformer[_, _], model: Option[_] = None) = {
		val mapping = in.zip(out).map({
			case (vIn: DenseVector[_], vOut: DenseVector[_]) => {
				new AllToOneMapping(vIn.size, 1, vOut.size, 1, List(in.id), List(out.id))
			}
			case (vIn: DenseVector[_], vOut: Int) => {
				new AllToOneMapping(vIn.size, 1, 1, 1, List(in.id), List(out.id))
			}
			case (vIn: DenseMatrix[_], vOut: DenseMatrix[_]) => {
				new AllToOneMapping(vIn.rows, vIn.cols, vOut.rows, vOut.cols, List(in.id), List(out.id))
			}
			case (vIn: Image, vOut: Image) => {
				new AllToOneMapping(vIn.flatSize, 1, vOut.flatSize, 1, List(in.id), List(out.id), Some(vIn.metadata))
			}
			case _ => None
		})
		new KeystoneLineage(in, out, mapping, transformer, model)
	}
}

object LinComKLineage{
	def apply[T](in: RDD[_], out:RDD[_], transformer: Transformer[_, _], model: Option[DenseMatrix[T]]) = {
		val m = model.getOrElse(None).asInstanceOf[DenseMatrix[T]]
		val mapping = in.zip(out).map({
			case (sIn: DenseVector[_], sOut: DenseVector[_]) => {
				new LinComMapping(sIn.size, 1, sOut.size, 1, m.rows, m.cols, List(in.id), List(out.id))
			}
			case (sIn: DenseMatrix[_], sOut: DenseMatrix[_]) => {
				new LinComMapping(sIn.rows, sIn.cols, sOut.rows, sOut.cols, m.rows, m.cols, List(in.id), List(out.id))
			}
			case _ => None
		})
		new KeystoneLineage(in, out, mapping, transformer, model)
	}
}

object RegionKLineage{
	def apply(in: RDD[_], out: RDD[_], ioList: RDD[List[(List[(Int, Int)], List[(Int, Int)])]], 
		transformer: Transformer[_, _], model: Option[_] = None) = {
		val mapping = ioList.map(l => {
			ContourMapping(l, List(in.id), List(out.id))
		})

		new KeystoneLineage(in, out, mapping, transformer, model)
		//new SubZeroMapping(ioList, List(in.id), List(out.id))
		//new SimpleMapping(ioList, List(in.id), List(out.id))
	}
}