WinnerWinner
============

A small Scala application that queries the list of winners of the Canadian
Breast Cancer Foundation calendar lottery and sends the result to two phone
numbers by SMS through Amazon SNS.

## Usage

WinnerWinner is currently hardcoded to require two input calendar ids and phone
numbers. In the future I might generalize it to any number. Ids and phone
numbers are passed to the application through environment variables. The
following environment variables are required:

```shell
ID1
ID2
PHONE1
PHONE2
```

You will also need to set credentials for Amazon SNS through whichever means
you prefer. If you deploy to Amazon Lambda you will likely use AWS user roles.
If you'd like to run it locally you can export:

```shell
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
```

To run locally, export the required environment variables, move to the project
directory and type:

```shell
sbt run
```

## Deploying

WinnerWinner is packaged using
[sbt-asembly](https://github.com/sbt/sbt-assembly). To build and package the
application, run:

```shell
sbt clean compile assembly
```

The packaged `.jar` file is larger than 10MB, so it is suggested that you upload
your file to Amazon S3. To create your Lambda function choose `Create a Lambda
Function`, then `Configure function` in the left toolbar. Give your function a
name and description, and choose Java 8 as the Runtime. Choose `Upload a file
from Amazon S3` as the Code Entry Type and paste in the URL for the link to
your file.

Set the four required environment variables, and in the Handler field type:

```scala
com.rgcase.winnerwinner.WinnerWinner::fetch
```

Choose a role with access to Amazon SNS. The maximum memory requirement is
typically around 105MB, and each invocation runs for around 11s. I think a large
portion of that comes from shutting down the AsyncHttpClient so I'll probably
look at replacing it.
