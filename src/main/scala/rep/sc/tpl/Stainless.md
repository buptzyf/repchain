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

## 封装scala代码

stainless的该特性可重用现有的java或scala库做验证。

### 重用TrieMap

以scala.collection.concurrent.TrieMap类为例，将它封装为stainless的数据类型：

```
import stainless.lang._
import stainless.annotation._

import scala.collection.concurrent.TrieMap

case class TrieMapWrapper[K, V](@extern theMap: TrieMap[K, V])
```

然而，stainless对TrieMap还是一无所知，为了解决这个问题，以上代码使用@extern注解，stainless将把这个类型当作“透明”。

### 外部方法

定义TrieMap的contains方法：
```
import stainless.lang._
import stainless.annotation._

import scala.collection.concurrent.TrieMap

case class TrieMapWrapper[K, V](@extern theMap: TrieMap[K, V]) {

  @extern
  def contains(k: K): Boolean = {
    theMap contains k
  }
}
```

由于theMap是一个“透明”的类型因此没有contain方法。标注该方法为@extern，stainless就不再抽取该方法的主体，在该方法内可以任意使用TrieMap的方法。

### Contracts

定义一个创建空map的方法：
```
object TrieMapWrapper {
  @extern
  def empty[K, V]: TrieMapWrapper[K, V] = {
    TrieMapWrapper(TrieMap.empty[K, V])
  }
}

def prop1 = {
  val wrapper = TrieMapWrapper.empty[Int, String]
  assert(!wrapper.contains(42)) // invalid
}
```
由于stainless对empty方法一无所知，因此在调用TrieMapWrapper.empty后stainless对wrapper性质无法作出任何推断。为了弥补这一缺憾，可以对empty方法加一个后置条件——对于任意类型K的键值k，调用TrieMapWrapper.empty后的结果不会contain任何键值k。可描述如下：
```
object TrieMapWrapper {
  @extern
  def empty[K, V]: TrieMapWrapper[K, V] = {
    TrieMapWrapper(TrieMap.empty[K, V])
  } ensuring { res =>
    forall((k: K) => !res.contains(k))
  }
}
```

### 将方法标注为“纯”

如果调用contain方法两次：
```
def prop1 = {
  val wrapper = TrieMapWrapper.empty[Int, String]
  assert(!wrapper.contains(42))
  assert(!wrapper.contains(42))
}
```
第二次断言将失败。原因是stainless默认外部的方法会修改该内部的状态。（因为stainless不关心该方法的内部实现，所以不知道该方法是否“纯”），如果将该方法添加@pure这一注解，stainless就会将这一方法当作纯函数看待：
```
case class TrieMapWrapper[K, V](@extern theMap: TrieMap[K, V]) {

  @extern @pure
  def contains(k: K): Boolean = {
    theMap contains k
  }
}
```
自此，两次断言将会成功。

### 在repchain中使用
目前想到的点主要有两个：
* 浮点数的表示：stainless提供的数据类型主要是使用real来顶替浮点数，但是由于使用不方便（比如想表示1.5需要传入Real(3,2)，第一个参数是分子，第二个参数是分母），且部分性质验证时会超时，因此可以考虑封装一个新的可以表示浮点的数据类型。（早期的想法：估计无法实现）
* kv：在repchain读写kv时，会访问外部的数据类型，可以类比上述的TrieMap来封装kv，将kv和现有的验证逻辑更好地结合。

## 超时问题

在进行一些定理时，有些证明在参数是BigInt时可以正常证明，但更换为Real则会超时，本质上是由于Stainless所依赖的SMT求解器中BigInt和Real的理论基础不同，详见[该回答](https://www.zhihu.com/question/65438076/answer/233925406)

### 为何无法封装一个浮点类型取代Real？

1. Real是Stainless的基本类型，Stainless的类型检查器做了检查
2. Stainless所调用的SMT求解器也是支持Real，详见[SMT的标准库](http://smtlib.cs.uiowa.edu/theories-Reals.shtml)

### 结论

通过封装创造基础类型得不到前端检查和底层理论的支持。

注：SMT求解器含有FloatingPoint，但因为不满足结合律，同时Stainless也没有进行支持，无法使用。

### 目前的解决方案

1. 设计一些简单的Real逻辑验证，保证Stainless不会超时
2. 复杂逻辑改用BigInt做

## 封装KV

### 目的
为了让Stainless协同现有Repchain代码工作，使用ContractContextWrapper封装ContractContext。

### 好处
可以将读写KV与纯函数代码部分写在一起，不必要每次接触KV都离开纯函数。简单的说，让Stainless将KV视作一个普通的Map，ContractContext的细节对于Stainless是透明的，Stainless只要调用ContractContextWrapper所提供的set和get等方法就可以完成与KV的交互。

### 做法
为了进行形式化验证，对从KV中读出的数值类型在装饰器中做类型转化，使得读或写的数据是支持Stainless的（比如BigInt、Real等）。但在装饰器所封装的ContractContext中，可以任意使用数据类型。
