package com.esri

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.sql.{Connection, DriverManager, PreparedStatement}
import javax.imageio.ImageIO

import com.esri.arcgis.server.json.JSONObject
import com.esri.arcgis.system.{IObjectConstruct, IPropertySet, IRESTRequestHandler}

import scala.collection.JavaConversions._

class ExportImageSOI extends AbstractSOI with IObjectConstruct {

  val colorMapper = new ColorMapper()

  var connection: Connection = _
  var preparedStatement: PreparedStatement = _
  var imagePNG: String = _
  var maxWidth: Double = _

  override def construct(propertySet: IPropertySet): Unit = {
    log.addMessage(3, 200, "ExportImageSOI::construct")

    colorMapper.construct()

    maxWidth = propertySet.getProperty("maxWidth").asInstanceOf[String].toDouble

    val json = new JSONObject(Map("Content-Type" -> "image/png"))
    imagePNG = json.toString()

    Class.forName(propertySet.getProperty("driver").asInstanceOf[String])

    connection = DriverManager.getConnection(
      propertySet.getProperty("connection").asInstanceOf[String],
      propertySet.getProperty("username").asInstanceOf[String],
      "" // No password
    )

    preparedStatement = connection.prepareStatement("select" +
      " count(1),round(geography_latitude(pickup),3),round(geography_longitude(pickup),3)" +
      " from taxistats where dow=1 and hod=2 and geography_intersects(pickup, ?)" +
      " group by 2,3 order by 1")
  }

  def doExportImage(operationInput: String, responseProperties: Array[String]) = {
    val jsonInput = new JSONObject(operationInput)
    val sizeRE = "^(\\d+),(\\d+)$".r
    val (imgw, imgh) = jsonInput.getString("size") match {
      case sizeRE(wt, ht) => (wt.toInt, ht.toInt)
      case _ => (400, 400)
    }
    val (xmin, ymin, xmax, ymax) = jsonInput.getString("bbox") match {
      case text: String => {
        val tokens = text.split(',')
        (tokens(0).toDouble, tokens(1).toDouble, tokens(2).toDouble, tokens(3).toDouble)
      }
      case _ => (1.0, 1.0, -1.0, -1.0)
    }
    val xdel = xmax - xmin
    val ydel = ymax - ymin

    val fillw = (imgw * 110.0 / xdel).toInt
    val fillw2 = fillw / 2

    val fillh = (imgh * 130.0 / ydel).toInt
    val fillh2 = fillh / 2

    val bi = new BufferedImage(imgw, imgh, BufferedImage.TYPE_INT_ARGB)
    val g = bi.createGraphics()
    try {
      g.setBackground(Color.WHITE)
      if (xdel < maxWidth && xmin < xmax && ymin < ymax) {
        val minlon = WebMercator.xToLongitude(xmin)
        val maxlon = WebMercator.xToLongitude(xmax)
        val minlat = WebMercator.yToLatitude(ymin)
        val maxlat = WebMercator.yToLatitude(ymax)
        val dellon = maxlon - minlon
        val dellat = maxlat - minlat
        // log.addMessage(3, 200, s"$minlon,$minlat,$maxlon,$maxlat")
        val sb = new StringBuilder("POLYGON((")
        sb.append(minlon).append(' ').append(minlat).append(',')
          .append(maxlon).append(' ').append(minlat).append(',')
          .append(maxlon).append(' ').append(maxlat).append(',')
          .append(minlon).append(' ').append(maxlat).append(',')
          .append(minlon).append(' ').append(minlat).append("))")
        preparedStatement.setString(1, sb.toString)
        val resultSet = preparedStatement.executeQuery
        try {
          while (resultSet.next) {
            val w = resultSet.getInt(1)
            // Bug in MemSQL loading of data, lat is x and lon is y !!
            val lon = resultSet.getDouble(2)
            val lat = resultSet.getDouble(3)
            val fx = (lon - minlon) / dellon
            val fy = 1.0 - (lat - minlat) / dellat
            val gx = (imgw * fx).toInt
            val gy = (imgh * fy).toInt
            g.setColor(colorMapper.getColor(w.min(255)))
            g.fillRect(gx - fillw2, gy - fillh2, fillw, fillh)
          }
        } finally {
          resultSet.close()
        }
        g.setColor(Color.GREEN)
      }
      else {
        g.setColor(Color.RED)
      }
      g.drawRect(0, 0, imgw - 1, imgh - 1)
    } finally {
      g.dispose()
    }

    responseProperties(0) = imagePNG

    val baos = new ByteArrayOutputStream(bi.getWidth * bi.getHeight)
    ImageIO.write(bi, "PNG", baos)
    baos.toByteArray
  }

  override def handleRESTRequest(capabilities: String,
                                 resourceName: String,
                                 operationName: String,
                                 operationInput: String,
                                 outputFormat: String,
                                 requestProperties: String,
                                 responseProperties: Array[String]
                                  ) = {

    log.addMessage(3, 200, s"r=$resourceName o=$operationName i=$operationInput f=$outputFormat")

    (operationName, outputFormat) match {
      case ("export", "image") => doExportImage(operationInput, responseProperties)
      case _ =>
        findRestRequestHandlerDelegate() match {
          case inst: IRESTRequestHandler => inst.handleRESTRequest(
            capabilities, resourceName, operationName, operationInput, outputFormat, requestProperties, responseProperties
          )
          case _ => null
        }
    }
  }

  override protected def preShutdown(): Unit = {
    log.addMessage(3, 200, "ExportImageSOI::preShutdown")
    connection.close()
  }
}
