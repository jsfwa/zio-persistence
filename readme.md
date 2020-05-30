### zio-persistence-journal
##### (cassandra only, for now)

#### Why?
Currently, only alternative for persistence is Akka framework, which based on actors hence 
struggles of lack of backpressure during replay.
It makes impossible to manipulate huge event logs(10m-100m+ per persistence id)

Our persistence implementation relies on ZStreams, provides light and flexible control over your eventsource pipelines

#### Usage

```scala
//check reference.conf for config setup

for {
  journal  <- ZIO.service[AsyncJournal]
  _ <- journal.persist(chunk)
  _ <- journal.saveSnapshot(PersistentEvent)
  _ <- journal.loadSnapshot(persistenceId, SnapshotCriteria)
  _ <- journal.replay(persistenceId, ReplayCriteria)
} yield ...

```

