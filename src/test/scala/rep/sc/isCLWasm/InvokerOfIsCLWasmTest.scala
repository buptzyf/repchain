package rep.sc.isCLWasm

import org.json4s.JsonAST.JObject
import org.scalatest._
import org.mockito.Mockito.{mock, when}
import org.mockito.ArgumentMatchers.any
import org.wasmer.Module
import rep.proto.rc2.{CertId, ChaincodeInput, Signature, Transaction}
import rep.sc.Shim

import java.io.FileInputStream
import java.nio.file.{Files, Paths}
import org.json4s.jackson.JsonMethods.parse
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import rep.sc.scalax.ContractContext

class InvokerOfIsCLWasmTest extends FunSpec with Matchers with BeforeAndAfterAll {
  var moduleSimple: Module = null
  var moduleProof: Module = null
  var abiSimple: JObject = null
  var abiProof: JObject = null
  var shim: Shim = null

  val filePathPrefix = "src/test/scala/rep/sc/isCLWasm/"

  override def beforeAll(): Unit = {
    val codeBytesSimple = new FileInputStream(filePathPrefix + "simple.wasm").readAllBytes
    moduleSimple = new Module(codeBytesSimple)
    val codeBytesProof = new FileInputStream(filePathPrefix + "proof.wasm").readAllBytes
    moduleProof = new Module(codeBytesProof)

    val abiStrSimple = Files.readString(Paths.get(filePathPrefix + "simple.abi.json"))
    abiSimple = parse(abiStrSimple).asInstanceOf[JObject]
    val abiStrProof = Files.readString(Paths.get(filePathPrefix + "proof.abi.json"))
    abiProof = parse(abiStrProof).asInstanceOf[JObject]
  }

  describe("Test the onAction method with the simple smart contract(无业务含义的简单合约)") {
    it("Should be called successfully with the well input") {
      shim = mock(classOf[Shim])
      when(shim.setVal(any(), any())).thenAnswer(new Answer[Unit] {
        // Do nothing
        override def answer(invocation: InvocationOnMock): Unit = {}
      })
      when(shim.getVal(any())).thenAnswer(new Answer[Array[Byte]] {
        override def answer(invocation: InvocationOnMock): Array[Byte] = null
      })
      var tx = new Transaction()
      tx = tx.copy(para = Transaction.Para.Ipt(ChaincodeInput("g", Seq("1"))))
      tx = tx.copy(signature = Some(Signature(Some(CertId("121000005l35120456.node1")))))
      val ctx = new ContractContext(shim, tx)
      val mockUtils = mock(classOf[Utils])
      when(mockUtils.serialize(any(), any(), any(), any(), any())).thenAnswer(new Answer[(Array[Byte], Int)] {
        override def answer(invocation: InvocationOnMock): (Array[Byte], Int) = {
          val args = invocation.getArguments
          System.out.println(args)
          (Array[Byte](), 0)
        }
      })
      when(mockUtils.deserialize(any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(new Answer[(Int, Int)] {
        // Do nothing
        override def answer(invocation: InvocationOnMock): (Int, Int) = {
          (0, 0)
        }
      })
      when(mockUtils.readBool(any(), any())).thenAnswer(new Answer[Boolean] {
        override def answer(invocation: InvocationOnMock): Boolean = true
      })
      val invokerOfIsCLWasm = new InvokerOfIsCLWasm(mockUtils)
      val result = invokerOfIsCLWasm.onAction(moduleSimple, abiSimple, ctx)
      result.code should be(0)
    }

    it("Should call shim's getVal method and setVal method with the correct parameters successfully") {
      var firstCallSetVal = true
      shim = mock(classOf[Shim])
      when(shim.setVal(any(), any())).thenAnswer(new Answer[Unit] {
        override def answer(invocation: InvocationOnMock): Unit = {
          val args = invocation.getArguments
          if (firstCallSetVal) {
            firstCallSetVal = false
            assert(args(0).equals("s1"))
            assert(args(1).asInstanceOf[Array[Byte]].sameElements(Array[Byte](3, 0, 0, 0, 3, 0, 0, 0, 97, 98, 99)))
          }
        }
      })
      when(shim.getVal(any())).thenAnswer(new Answer[Array[Byte]] {
        override def answer(invocation: InvocationOnMock): Array[Byte] = {
          val args = invocation.getArguments
          if (args(0).equals("s1")) return Array[Byte](1, 0, 0, 0, 3, 0, 0, 0, 99, 98, 97)
          null
        }
      })
      var tx = new Transaction()
      tx = tx.copy(para = Transaction.Para.Ipt(ChaincodeInput("g", Seq("2"))))
      tx = tx.copy(signature = Some(Signature(Some(CertId("121000005l35120456.node1")))))
      val ctx = new ContractContext(shim, tx)
      val utils = new Utils
      val invokerOfIsCLWasm = new InvokerOfIsCLWasm(utils)
      val result = invokerOfIsCLWasm.onAction(moduleSimple, abiSimple, ctx)
      result.code should be(0)
    }

    it("Should accept the multiple json strings with different types as the parameters of a smart contract function") {
      shim = mock(classOf[Shim])
      when(shim.setVal(any(), any())).thenAnswer(new Answer[Unit] {
        override def answer(invocation: InvocationOnMock): Unit = {
          val args = invocation.getArguments
          assert(args(0).equals("s1"))
          assert(args(1).asInstanceOf[Array[Byte]].sameElements(Array[Byte](0, 0, 0, 0, 0, 0, 0, 0)))
        }
      })
      when(shim.getVal(any())).thenAnswer(new Answer[Array[Byte]] {
        override def answer(invocation: InvocationOnMock): Array[Byte] = null
      })

      var tx = new Transaction()
      tx = tx.copy(para = Transaction.Para.Ipt(ChaincodeInput("get_from_list", Seq("[1,2,3]", "1"))))
      tx = tx.copy(signature = Some(Signature(Some(CertId("121000005l35120456.node1")))))
      val ctx = new ContractContext(shim, tx)
      val utils = new Utils
      val invokerOfIsCLWasm = new InvokerOfIsCLWasm(utils)
      val result = invokerOfIsCLWasm.onAction(moduleSimple, abiSimple, ctx)
      result.code should be(0)
    }

    it("Should throw exception with the not well parameters of a smart contract function") {
      shim = mock(classOf[Shim])
      when(shim.setVal(any(), any())).thenAnswer(new Answer[Unit] {
        // Do nothing
        override def answer(invocation: InvocationOnMock): Unit = {
          val args = invocation.getArguments
          assert(args(0).equals("s1"))
          assert(args(1).asInstanceOf[Array[Byte]].sameElements(Array[Byte](0, 0, 0, 0, 0, 0, 0, 0)))
        }
      })
      when(shim.getVal(any())).thenAnswer(new Answer[Array[Byte]] {
        override def answer(invocation: InvocationOnMock): Array[Byte] = null
      })

      var tx = new Transaction()
      // 从List [1,2,3]中获取index为3的元素，已经超出其范围
      tx = tx.copy(para = Transaction.Para.Ipt(ChaincodeInput("get_from_list", Seq("[1,2,3]", "3"))))
      tx = tx.copy(signature = Some(Signature(Some(CertId("121000005l35120456.node1")))))
      val ctx = new ContractContext(shim, tx)
      val utils = new Utils
      val invokerOfIsCLWasm = new InvokerOfIsCLWasm(utils)
      val exception = intercept[Exception] {
        invokerOfIsCLWasm.onAction(moduleSimple, abiSimple, ctx)
      }
      exception.getMessage should include("index out of bounds")
    }
  }

  describe("Test the onAction method with the proof smart contract(存证合约)") {
    it("Should be called successfully with the well input") {
      shim = mock(classOf[Shim])
      when(shim.setVal(any(), any())).thenAnswer(new Answer[Unit] {
        override def answer(invocation: InvocationOnMock): Unit = {
          val args = invocation.getArguments
          // world state proof is an instance of Map[String, String] in isCL
          assert(args(0).equals("proof"))
          assert(args(1).asInstanceOf[Array[Byte]].sameElements(
            Array[Byte](
              4, 0, 0, 0, 2, 0, 0, 0,  // _capacity and _len
              1, 0, 0, 0, 97, 4, 0, 0, 0, 107, 101, 121, 49, 0, 0, 0, 0, 0, 0, 0, 0, // keys: "a", "key1"
              1, 0, 0, 0, 98, 6, 0, 0, 0, 118, 97, 108, 117, 101, 49, 0, 0, 0, 0, 0, 0, 0, 0 //values: "b", "value1"
            )
          ))
        }
      })
      when(shim.getVal(any())).thenAnswer(new Answer[Array[Byte]] {
        override def answer(invocation: InvocationOnMock): Array[Byte] = null
      })
      var tx = new Transaction()
      tx = tx.copy(para = Transaction.Para.Ipt(ChaincodeInput("putProof", Seq("\"key1\"", "\"value1\""))))
      tx = tx.copy(signature = Some(Signature(Some(CertId("121000005l35120456.node1")))))
      val ctx = new ContractContext(shim, tx)
      val utils = new Utils
      val invokerOfIsCLWasm = new InvokerOfIsCLWasm(utils)
      val result = invokerOfIsCLWasm.onAction(moduleProof, abiProof, ctx)
      result.code should be(0)
    }

    it("Should be called successfully with the well input and the stored world state data") {
      shim = mock(classOf[Shim])
      when(shim.setVal(any(), any())).thenAnswer(new Answer[Unit] {
        override def answer(invocation: InvocationOnMock): Unit = {
          val args = invocation.getArguments
          assert(args(0).equals("proof"))
          assert(args(1).asInstanceOf[Array[Byte]].sameElements(
            Array[Byte](
              4, 0, 0, 0, 3, 0, 0, 0, // _capacity and _len
              4, 0, 0, 0, 107, 101, 121, 48, 1, 0, 0, 0, 97, 4, 0, 0, 0, 107, 101, 121, 49, 0, 0, 0, 0,  // keys: "key0", "a", "key1"
              6, 0, 0, 0, 118, 97, 108, 117, 101, 48, 1, 0, 0, 0, 98, 6, 0, 0, 0, 118, 97, 108, 117, 101, 49, 0, 0, 0, 0 // values: "value0", "b", "value1"
            )
          ))
        }
      })
      when(shim.getVal(any())).thenAnswer(new Answer[Array[Byte]] {
        override def answer(invocation: InvocationOnMock): Array[Byte] = {
          // Assume we already stored the world state data of isCL:  Map[String, String] proof = ("key0" -> "value0")
          Array[Byte](
            4, 0, 0, 0, 1, 0, 0, 0, // _capacity and _len
            4, 0, 0, 0, 107, 101, 121, 48, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,  // keys: "key0"
            6, 0, 0, 0, 118, 97, 108, 117, 101, 48, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 // keys: "value0"
          )
        }
      })
      var tx = new Transaction()
      tx = tx.copy(para = Transaction.Para.Ipt(ChaincodeInput("putProof", Seq("\"key1\"", "\"value1\""))))
      tx = tx.copy(signature = Some(Signature(Some(CertId("121000005l35120456.node1")))))
      val ctx = new ContractContext(shim, tx)
      val utils = new Utils
      val invokerOfIsCLWasm = new InvokerOfIsCLWasm(utils)
      val result = invokerOfIsCLWasm.onAction(moduleProof, abiProof, ctx)
      result.code should be(0)
    }
  }
}
