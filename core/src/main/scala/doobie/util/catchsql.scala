package doobie.util

import java.sql.SQLException
import scalaz.{ Monad, Catchable, \/, -\/, \/- }
import scalaz.syntax.bifunctor._
import scalaz.syntax.monad._
import scalaz.syntax.catchable._

import doobie.enum.sqlstate.SqlState
import doobie.syntax.catchable._

/** 
 * Module of additional combinators for `Catchable`, specific to `SQLException`.
 */
object catchsql {

  /** Like `attempt` but catches only `SQLException`. */
  def attemptSql[M[_]: Monad: Catchable, A](ma: M[A]): M[SQLException \/ A] =
    ma.attempt.map(_.leftMap {
      case sqle: SQLException => sqle
      case e                  => throw e
    })

  /** Like `attemptSql` but yields only the exception's `SqlState`. */
  def attemptSqlState[M[_]: Monad: Catchable, A](ma: M[A]): M[SqlState \/ A] =
    attemptSql(ma).map(_.leftMap(e => SqlState(e.getSQLState)))

  def attemptSomeSqlState[M[_]: Monad: Catchable, A, B](ma: M[A])(f: PartialFunction[SqlState, B]): M[B \/ A] =
    attemptSql(ma).map(_.leftMap(sqle => f.lift(SqlState(sqle.getSQLState)).getOrElse(throw sqle)))

  /** Executes the handler, for exceptions propagating from `ma`. */
  def exceptSql[M[_]: Monad: Catchable, A](ma: M[A])(handler: SQLException => M[A]): M[A] =
    attemptSql(ma).flatMap(_.bimap(handler, _.point[M]).merge)

  /** Executes the handler, for exceptions propagating from `ma`. */
  def exceptSqlState[M[_]: Monad: Catchable, A](ma: M[A])(handler: SqlState => M[A]): M[A] =
    exceptSql(ma)(e => handler(SqlState(e.getSQLState)))

  /** Executes the handler where defined, for exceptions propagating from `ma`. */
  def exceptSomeSqlState[M[_]: Monad: Catchable, A](ma: M[A])(pf: PartialFunction[SqlState, M[A]]): M[A] =
    exceptSql(ma)(e => pf.lift(SqlState(e.getSQLState)).getOrElse((throw e): M[A]))

  /** Like "finally", but only performs the final action if there was an exception. */
  def onSqlException[M[_]: Monad: Catchable, A, B](ma: M[A])(action: M[B]): M[A] =
    exceptSql(ma)(e => action *> Catchable[M].fail(e))

}

