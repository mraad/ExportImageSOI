package com.esri

import com.esri.hex.HexGrid

class ScaleLoc(scale: Double, val loc: String) extends Serializable {
  def find(mapScale: Double) = {
    mapScale <= scale
  }

  def toHexGrid() = {
    loc.split('_') match {
      case Array(_, size) => {
        HexGrid(size.toDouble, -20000000.0, -20000000.0)
      }
      case _ => HexGrid(1000000.0, -20000000.0, -20000000.0)
    }
  }
}

object ScaleLoc extends Serializable {
  def apply(text: String) = {
    text.split(':') match {
      case Array(scale, loc) => new ScaleLoc(scale.toDouble, loc)
      case _ => new ScaleLoc(Double.PositiveInfinity, text)
    }
  }
}
