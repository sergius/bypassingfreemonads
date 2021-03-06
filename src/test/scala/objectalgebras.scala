package org.hablapps.talk
package bypassingfreemonads

import org.scalatest._

object ObjectAlgebraApproach extends ObjectAlgebraApproach

class ObjectAlgebraApproach extends FlatSpec with Matchers{
    
  // IO algebras

  trait IO[P[_]]{
    def read(): P[String]
    def write(msg: String): P[Unit]
  }

  object IO{

    def apply[P[_]](implicit IO: IO[P]) = IO

    object Syntax{
      def read[P[_]]()(implicit IO: IO[P]): P[String] = 
        IO.read()

      def write[P[_]](msg: String)(implicit IO: IO[P]): P[Unit] = 
        IO.write(msg)
    }
  }

  // Particular IO Program
  
  object SingleEffectProgram{
    import scalaz.Monad
    import IO.Syntax._, scalaz.syntax.monad._

    def echo[P[_]: IO: Monad](): P[String] = for{
      msg <- read()
      _ <- write(msg)
    } yield msg
  } 

  // Console-based interpretation of IO Programs

  import scalaz.Id, Id.Id

  object Console{
    implicit object ConsoleIO extends IO[Id]{
      def read() = scala.io.StdIn.readLine()
      def write(msg: String) = println(msg)
    }
  }

  // State-based interpretation of IO Programs

  import scalaz.State

  case class IOState(in: List[String], out: List[String])

  type IOStateAction[T]=State[IOState,T]

  object IOStateAction{
    implicit object IOStateActionIO extends IO[IOStateAction]{
      import scalaz.syntax.monad._
      
      def read() = State.get[IOState] >>= {
        case IOState(msg::reads,written) => 
          State.put(IOState(reads,written)) >| msg
      }

      def write(msg: String) = State.get[IOState] >>= {
        case IOState(reads, written) => 
          State.put(IOState(reads, msg::written))
      }
    }
  }

  // Echo interpretations
  
  object SingleEffectInterpretations{
    import SingleEffectProgram._
    import scalaz.Monad

    def consoleEcho(): String = 
      echo()(Console.ConsoleIO, Monad[Id])

    import IOStateAction._
    def stateEcho(): IOStateAction[String] = 
      echo[IOStateAction]()
  }

  "State-based echo" should "work" in {
    import SingleEffectInterpretations._

    stateEcho().eval(IOState(List("hi"),List())) shouldBe 
      "hi"

    stateEcho().exec(IOState(List("hi"),List())) shouldBe
      IOState(List(),List("hi"))
  }

  // Interpretations over IOState 
  
  sealed abstract class Level
  case object WARNING extends Level
  case object DEBUG extends Level
  case object INFO extends Level
  case object TRACE extends Level
  
  trait Log[P[_]]{
    def log(level: Level, msg: String): P[Unit]
    def trace(msg: String): P[Unit] = log(TRACE,msg)
  }

  object Log{
    object Syntax{
      def log[P[_]](level: Level, msg: String)(implicit Log: Log[P]) = 
        Log.log(level,msg)
      def trace[P[_]](msg: String)(implicit Log: Log[P]) = 
        Log.trace(msg)
    }

    implicit object IOStateActionLog extends Log[IOStateAction]{
      def log(level: Level, msg: String) = 
        IOStateAction.IOStateActionIO.write(s"$level: $msg")
    }

    implicit object ConsoleLog extends Log[Id]{
      def log(level: Level, msg: String) = 
        println(s"$level: $msg")
    }

  }

  // Generic programs

  object MultipleEffectAbstractProgram{
    import scalaz.Monad
    import IO.Syntax._, Log.Syntax._, scalaz.syntax.monad._

    def echo[P[_]: IO: Log: Monad](): P[String] = for {
      msg <- read()
      _ <- trace(s"read '$msg'")
      _ <- write(msg)
      _ <- trace(s"written '$msg'")
    } yield msg
  }

  "IO with logging programs" should "work with io actions" in {
    import MultipleEffectAbstractProgram._
    import IOStateAction._
    
    val init: IOState = IOState(List("hi"),List())

    echo[IOStateAction]().eval(init) shouldBe 
      "hi"

    echo[IOStateAction]().exec(init) shouldBe 
      IOState(List(), List("TRACE: written 'hi'", "hi", "TRACE: read 'hi'"))
  }

 }