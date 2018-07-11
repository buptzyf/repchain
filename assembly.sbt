// put this at the top of the file
// 这个是打包之后jar包的名字
assemblyJarName in assembly := "RepChain.jar"

// 这个作用是在打包的时候，跳过测试
test in assembly := {}