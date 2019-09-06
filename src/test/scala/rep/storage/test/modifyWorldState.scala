package rep.storage.test

import rep.storage.ImpDataAccess
import org.json4s.{ DefaultFormats, jackson }
import org.json4s.native.Serialization.{ write, writePretty }
import rep.protos.peer.CertId
import rep.protos.peer.Signature
import java.util.Date

import rep.crypto.Sha256
import scala.collection.mutable
import rep.storage.util.pathUtil
import scala.math._

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

//import scala.collection.immutable._

import java.nio.ByteBuffer
import rep.storage.util.pathUtil

import scalapb.json4s.JsonFormat
import org.json4s.{DefaultFormats, Formats, jackson}
import org.json4s.jackson.JsonMethods._
import org.json4s.DefaultFormats._

import scala.collection.mutable.{ArrayBuffer,LinkedHashMap}
import rep.utils.SerializeUtils.serialise
import  _root_.com.google.protobuf.ByteString 

object modifyWorldState extends App {
  val da1 = ImpDataAccess.GetDataAccess("121000005l35120456.node1")
  val key = ""
  val value = 3
  da1.Put(key, serialise(value))
}