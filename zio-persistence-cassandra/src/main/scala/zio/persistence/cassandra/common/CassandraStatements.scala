package zio.persistence.cassandra.common

import zio.Task
import zio.cassandra.service.Session
import zio.persistence.journal.Done

trait CassandraStatements {

  def config: CassandraConfig

  def init(session: Session): Task[Done] = {
    if(!config.migration)
      Task.succeed(())
    else
      for{
        _ <- session.execute(keyspaceQuery)
        _ <- session.execute(tableQuery)
      } yield ()
  }

  def tableName : String = s"${config.keyspace}.${config.tableName}"

  def keyspaceQuery : String

  def tableQuery : String

  def writeQuery : String

  def selectQuery : String
}
