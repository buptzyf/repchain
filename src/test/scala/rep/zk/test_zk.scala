package rep.zk

import java.net.URL
import java.io.File

object test_zk extends App {


  val p1 = Pairing.P1()
  val p2 = Pairing.P2()
  Pairing.pairingProd1(p1, p2)
}
