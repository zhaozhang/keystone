package lineage

import utils.ImageMetadata

abstract class SubSpace extends Serializable {
  def contain(c: Coor): Boolean
  def expand(): List[Coor]
}

case class Vector(dim: Int) extends SubSpace {
  override def contain(c: Coor) = {
    c match {
      case coor: Coor1D => if (coor.x < dim) true else false
      case _ => false
    }
  }

  override def expand() = (0 until dim).toList.map(Coor(_))
  override def toString(): String = "Vector: "+dim
}

case class Matrix(xDim: Int, yDim: Int) extends SubSpace {
  override def contain(c: Coor) = {
    c match {
      case coor: Coor2D => if ((coor.x < xDim)&&(coor.y < yDim)) true else false
      case _ => false
    }
  }

  override def expand() = (for(i <- 0 until xDim; j <- 0 until yDim) yield Coor(i,j)).toList
  override def toString(): String = "Matrix: "+xDim+"x"+yDim
}

case class Image(xDim: Int, yDim: Int, cDim: Int) extends SubSpace {
  override def contain(c: Coor) = {
    c match {
      case coor: Coor3D => if ((coor.x < xDim)&&(coor.y < yDim)&&(coor.c < cDim)) true else false
      case _ => false
    }
  }
  override def expand  = (for(i <- 0 until xDim; j <- 0 until yDim; c <- 0 until cDim) yield Coor(i,j,c)).toList
  override def toString(): String =  "Image: "+xDim+"x"+yDim+"x"+cDim 
}

object SubSpace {
  def apply(dim: Int): SubSpace = Vector(dim)
  def apply(xDim: Int, yDim: Int): SubSpace = Matrix(xDim, yDim)
  def apply(xDim: Int, yDim: Int, cDim: Int): SubSpace = Image(xDim, yDim, cDim)
  def apply(meta: ImageMetadata): SubSpace = Image(meta.xDim, meta.yDim, meta.numChannels)
}