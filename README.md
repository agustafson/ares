Redscaler
=========

[![Build Status](https://travis-ci.org/agustafson/redscaler.svg?branch=master)](https://travis-ci.org/agustafson/redscaler)

Redscaler is a purely functional scala redis client, using fs2 under the hood.

```scala
import cats.syntax.cartesian._
import fs2.Task
import redscaler._
import redscaler.ConnectionOps.ops
import redscaler.ConnectionOps.ops.ConnectionIO

val transactor = Transactor[Task]("127.0.0.1", 6379)

val query: ConnectionIO[ErrorOr[Option[Vector[Byte]]]] = ops.get("foo")

val result: ErrorOr[Option[Vector[Byte]]] = transactor.transact(query).unsafeRun()

case class Employee(id: Int, name: String, dateOfBirth: DateTime)

case class Company(id: Int)

def updateEmployeeDetails(employee: Employee): ConnectionIO[Unit] =
  ops.hmSet(s"employee:${employee.id}", Seq("name" -> employee.name, "dateOfBirth" -> employee.dateOfBirth))
  
def addEmployee(employee: Employee, company: Company): ConnectionIO[Unit] =
  ops.sAdd(s"company:${company.id}:employees", employee.id)
  
def getEmployees(company: Company): ConnectionIO[Set[Employee]] =
  ops.sMembers(s"company:${company.id}:employees")
  
def addNewEmployee(employee: Employee, company: Company): ConnectionIO[Set[Employee]] = {
  updateEmployeeDetails(employee) *>
    addEmployee(employee, company) *>
    getEmployees(company)
}
```