package com.esri

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.sql.{Connection, DriverManager, PreparedStatement}
import javax.imageio.ImageIO

import com.esri.arcgis.interop.extn.ArcGISExtension
import com.esri.arcgis.server.json.JSONObject
import com.esri.arcgis.system.{IObjectConstruct, IPropertySet, IRESTRequestHandler}
import com.esri.webmercator._

import scala.collection.JavaConversions._

@ArcGISExtension
class ExportImageSOI extends AbstractSOI with IObjectConstruct {

  val colorMapper = new ColorMapper()

  var connection: Connection = _
  var preparedStatement: PreparedStatement = _
  var imagePNG: String = _
  var maxWidth: Double = _
  var minCount: Double = _
  var maxCount: Double = _
  var delCount: Double = _
  var scaleMax: Double = _
  var scaleLocArr: Array[ScaleLoc] = _
  val dpm = 96.0 /*DPI*/ * 39.3700787 // dots per meter

  override def construct(propertySet: IPropertySet): Unit = {
    log.addMessage(3, 200, "ExportImageSOI::construct")

    try {
      colorMapper.construct()

      minCount = propertySet.getProperty("minCount").asInstanceOf[String].toDouble
      maxCount = propertySet.getProperty("maxCount").asInstanceOf[String].toDouble
      delCount = maxCount - minCount

      scaleMax = propertySet.getProperty("maxScale").asInstanceOf[String].toDouble
      scaleLocArr = propertySet.getProperty("scales").asInstanceOf[String].split(',').map(ScaleLoc(_))

      maxWidth = propertySet.getProperty("maxWidth").asInstanceOf[String].toDouble
      val tableName = propertySet.getProperty("table").asInstanceOf[String]

      val json = new JSONObject(Map("Content-Type" -> "image/png"))
      imagePNG = json.toString()

      Class.forName(propertySet.getProperty("driver").asInstanceOf[String])

      connection = DriverManager.getConnection(
        propertySet.getProperty("connection").asInstanceOf[String],
        propertySet.getProperty("username").asInstanceOf[String],
        "" // No password
      )

      /*
            preparedStatement = connection.prepareStatement(
              s"""select
               count(1),round(geography_latitude(shape),3),round(geography_longitude(shape),3)
              from $tableName where geography_intersects(shape, ?)
              group by 2,3 order by 1"""
            )
      */
      preparedStatement = connection.prepareStatement(
        s"""select ?,count(1)
            from $tableName where geography_intersects(shape, ?)
        group by 1"""
      )

      log.addMessage(3, 200, "Constructed.")
    }
    catch {
      case t: Throwable => log.addMessage(3, 200, t.toString())
    }
  }

  def doExportImage(operationInput: String, responseProperties: Array[String]) = {
    val jsonInput = new JSONObject(operationInput)
    val sizeRE = "^(\\d+),(\\d+)$".r
    val (imgW, imgH) = jsonInput.getString("size") match {
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

    val bi = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB)
    val g = bi.createGraphics()
    try {
      g.setBackground(Color.WHITE)
      val scale = (xmax - xmin) * dpm / imgW
      if (scale < scaleMax) {
        scaleLocArr.find(_.find(scale)) match {
          case Some(scaleLoc) =>
            try {
              val hexGrid = scaleLoc.toHexGrid
              val minLon = xmin toLongitude
              val maxLon = xmax toLongitude
              val minLat = ymin toLatitude
              val maxLat = ymax toLatitude
              val delLon = maxLon - minLon
              val delLat = maxLat - minLat
              val sb = new StringBuilder("POLYGON((")
              sb.append(minLon).append(' ').append(minLat).append(',')
                .append(maxLon).append(' ').append(minLat).append(',')
                .append(maxLon).append(' ').append(maxLat).append(',')
                .append(minLon).append(' ').append(maxLat).append(',')
                .append(minLon).append(' ').append(minLat).append("))")
              preparedStatement.setString(1, scaleLoc.loc)
              preparedStatement.setString(2, sb.toString)
              val resultSet = preparedStatement.executeQuery
              try {
                while (resultSet.next) {
                  val loc = resultSet.getString(1)
                  val pop = resultSet.getInt(2)
                  val arr = loc.split(':')
                  val row = arr(0).toLong
                  val col = arr(1).toLong
                  val cellXY = hexGrid.convertRowColToHexXY(row, col)
                  val lon = cellXY.x toLongitude
                  val lat = cellXY.y toLatitude
                  val fx = (lon - minLon) / delLon
                  val fy = 1.0 - (lat - minLat) / delLat
                  val gx = (imgW * fx).toInt
                  val gy = (imgH * fy).toInt
                  val colorIndex = if (pop < minCount) 0
                  else if (pop > maxCount) 255
                  else math.floor(255 * (pop - minCount) / delCount).toInt
                  g.setColor(colorMapper.getColor(colorIndex))
                  g.fillRect(gx - 10, gy - 10, 20, 20)
                }
              } finally {
                resultSet.close()
              }
              g.setColor(Color.GREEN)
            }
            catch {
              case t: Throwable => {
                log.addMessage(3, 200, t.toString)
                g.setColor(Color.RED)
              }
            }
          case _ => {
            g.setColor(Color.BLUE)
          }
        }
      }
      else {
        g.setColor(Color.BLUE)
      }
      g.drawRect(0, 0, imgW - 1, imgH - 1)
    }
    finally {
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
                                )

  = {

    // log.addMessage(3, 200, s"r=$resourceName o=$operationName i=$operationInput f=$outputFormat")
    log.addMessage(3, 200, s"$requestProperties")

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

  override protected def preShutdown(): Unit

  = {
    log.addMessage(3, 200, "ExportImageSOI::preShutdown")
    try {
      connection.close()
    }
    catch {
      case t: Throwable => log.addMessage(3, 200, t.toString)
    }
  }
}
