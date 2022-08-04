package rep.app

import java.nio.file.Paths
import rep.utils.genesis.CreateGenesisTool


object GenesisBlockGenerator {

  def generate(fileName: String):Unit ={
    val name = fileName.substring(0,fileName.lastIndexOf("."))
    val filePath = Paths.get("conf",fileName)
    val userConfigFile = filePath.toFile
    if(!userConfigFile.exists()){
      System.err.println("Please create a Genesis block configuration file in the conf directory," +
        " and enter the name of the genesis block configuration file")
    }else{
      val tool = new CreateGenesisTool(userConfigFile)
      val r = tool.buildGenesisFile(name)
      if(r){
        System.out.println("Creation block file successfully established")
      }else{
        System.err.println("Creation block file failed")
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val defaultFileName = "genesis.conf"
    if(args != null && args.length == 1){
      generate(args(0))
    }else{
      generate((defaultFileName))
    }
  }
}
