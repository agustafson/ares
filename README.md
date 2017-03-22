Redscaler
=========

[![Build Status](https://travis-ci.org/agustafson/redscaler.svg?branch=master)](https://travis-ci.org/agustafson/redscaler)

Redscaler is a purely functional scala redis client, using fs2 under the hood.

```scala
import fs2.Task
import redscaler._
import redscaler.ConnectionOps.ops

val transactor = Transactor[Task]("127.0.0.1", 6379)

val query: ops.ConnectionIO[ErrorOr[Option[Vector[Byte]]]] = ops.get("foo")

val result: ErrorOr[Option[Vector[Byte]]] = transactor.transact(query).unsafeRun()
```

