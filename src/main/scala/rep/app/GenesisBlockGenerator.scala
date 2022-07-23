package rep.app

import java.nio.file.Paths
import rep.utils.genesis.CreateGenesisTool

object GenesisBlockGenerator {
  def main(args: Array[String]): Unit = {
    if(args != null && args.length == 1){
      val fileName = args(0)
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
          System.out.println("Creation block file failed")
        }
      }
    }else{
      System.err.println("Please create a Genesis block configuration file in the conf directory," +
        " and enter the name of the genesis block configuration file")
    }
  }
}
