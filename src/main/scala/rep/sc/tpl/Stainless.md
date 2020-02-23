# Stainless

## 参考阅读
- [Stainless文档](https://epfl-lara.github.io/stainless/)
- [Stainless源码](https://github.com/epfl-lara/stainless)

## 环境搭建
根据stainless文档建议的最简安装方法，下载解压[Release版](https://github.com/epfl-lara/stainless/releases)，将解压后得到的可执行文件stainless加入环境变量。

## 执行命令
到对应目录，执行stainless --timeout=200 SupplyTPLByStainless.scala。其中timeout选项指定超时时间。

## 定理证明
以下使用一个简单的定理证明对Stainless的使用做一个介绍。

### 两个定理
```
import stainless.collection._ // for List
import stainless.lang._       // for holds

object Example {
  def rightUnitAppend[T](l1: List[T]): Boolean = {
    l1 ++ Nil() == l1
  }.holds
}
```

这个例子描述了一个简单的定理，即任意一个列表尾部接一个空列表还是这个列表。其中.holds代表该方法的返回值应该永远为真。这个定理很明显是成立的，但如果使用stainless来验证该定理时，stainless会给出unknown这一结果，本质是超时了（times out），也就是stainless无法找到这一定理成立的理由。

stainless无法对上面这个定理自动完成证明。再看以下定理：

```
def leftUnitAppend[T](l1: List[T]): Boolean = {
  Nil() ++ l1 == l1
}.holds
```

这个定理描述了一个空列表后接任意一个列表都应该等于这个后接的列表，与上面的例子相比其实就是比较运算符左侧的两项交换了一下位置，依然是显然成立的定理。stainless在验证该定理时给出valid结果。


### 不能自动证明的原因
为什么会出现这种情况？仅仅是交换了一下比较运算符左侧两项位置stainless却给出了不同的结论，在说明原因前，先看一下列表连接符++在stainless中的定义：

```
def ++(that: List[T]): List[T] = (this match {
  case Nil()       => that
  case Cons(x, xs) => Cons(x, xs ++ that)
}) ensuring { res => /* ... */ }
```

根据定义，列表连接符++将左侧的列表项this分为两种情况讨论：
- 如果左侧列表为空列表，直接返回that列表；
- 如果左侧列表不是空列表，将其第一项定义为x，除第一项外组成的列表为xs，返回首项是x，剩余部分是xs ++ that的列表。很明显，xs ++ that会继续调用列表连接符++的方法。由于每次传入的xs相比上次调用的xs都会少一个首项x，xs一定会越来越短最终变为空列表，匹配到左侧列表是空列表的情况中，直接返回that列表，所以递归调用终会结束。

现在可以知道为什么上面两个定理一个stainless会直接通过，而另一个无法通过。
- Nil() ++ l1 中很明确。this列表是空列表Nil()，因此列表连接符++会直接匹配case Nil()，返回l1，最后比较表达式会化简为l1 == l1恒成立，定理证毕；
- l1 ++ Nil() 中不明确。this列表是l1，stainless会调用列表连接符++，在执行匹配时由于对l1一无所知，因此不知道应该匹配哪一个case，自此定理证明卡在了这里。

### 手动定理证明

既然stainless对l1 ++ Nil()中的l1一无所知，我们可以辅助stainless让它对l1分情况讨论：
- 当l1是空列表时；
- 当l1是非空列表时。
当上述两种情况l1 ++ Nil() == l1都成立，也就可以称l1 ++ Nil() == l1成立。

```
import stainless.collection._ // for List
import stainless.lang._       // for holds
import stainless.proof._      // for because

object Example {
  def rightUnitAppend[T](l1: List[T]): Boolean = {
    (l1 ++ Nil() == l1) because {
      l1 match {
        case Nil()       => true
        case Cons(x, xs) => rightUnitAppend(xs)
      }
    }
  }.holds
}
```
这段手动定理证明代码的含义：
- l1 ++ Nil() == l1声明了我们想要证明的定理；
- because 关键字：合取联结词的语法糖，后接手动引导定理的证明；
- 对列表l1分情况讨论：
+ 当l1为空列表Nil()时，stainless化简显然易见得Nil() ++ Nil() == Nil()，直接返回true即可；
+ 当l1为非空列表时，首项记为x，除首项之外的项记为列表xs，左右两侧相同的x消去，化简为xs ++ Nil() == xs，也就相当于再次调用rightUnitAppend(xs)，由于xs越来越短最终xs总会匹配到第一种情况返回true，自此第二种情况也成立。

当列表l1可能的两种情况都为true时，l1 ++ Nil() == l1也成立，证毕。

### 结论
手动证明其实就是在stainless无法自行推断或化简表达式遇到困难时，人为地将证明过程的全部或部分写出来，给stainless一些提示（hints）让它继续证明下去。