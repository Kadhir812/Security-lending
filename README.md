# Local application and Kafka project

This project keeps the frontend and backend alongside a local Apache Kafka
broker. Kafka runs in single-node KRaft mode; no ZooKeeper service is needed.

## Layout

```text
.
├── frontend/                 # Frontend application
├── backend/                  # Backend application
├── kafka/                    # Apache Kafka distribution (4.4.0; ignored by Git)
│   ├── bin/
│   ├── config/
│   │   └── kraft/server.properties
│   └── libs/
├── kafka-runtime/
│   ├── data/                 # KRaft metadata and broker topic data
│   └── brokER-logs/          # Kafka process log and PID file
├── app-logs/
│   ├── create-topic.log      # Topic creation output
│   ├── producer.log          # Messages and diagnostics from produce.sh
│   └── consumer.log          # Output and diagnostics from consume.sh
├── scripts/                  # Kafka command wrappers
├── config/
│   ├── kafka.env             # Cluster ID, relative paths, and broker defaults
│   └── kraft/server.properties
├── .gitignore
└── README.md
```

Kafka binaries, runtime data, and generated application logs are excluded from
version control. Extract the official Kafka 4.4.0 binary archive directly into
`kafka/` so `kafka/bin/`, `kafka/config/`, and `kafka/libs/` exist at those paths.
`kafka.env` contains paths relative to this project root so the wrappers can
be run from any working directory.

## Requirements

- Java 17 or newer on `PATH`
- Windows PowerShell and the Windows tools included under `kafka/bin/windows/`
- Git Bash for the `.sh` wrappers; WSL and Docker are not required

## Windows installation and first-time setup

Open PowerShell as your regular user (do not use **Run as administrator**) and
run:

```powershell
Set-Location 'C:\secuity lending'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup-kafka.ps1
```

`-ExecutionPolicy Bypass` applies only to that PowerShell process; it does not
change the machine's execution policy.
The script uses the Kafka 4.4.0 distribution already in `kafka/`. If that
distribution is missing, it downloads the official Apache binary archive,
checks its SHA-512 checksum, and extracts it into the project. It copies the
tracked `config/kraft/server.properties` into Kafka's config folder, creates
the project-local runtime directories, generates a cluster ID with
`kafka-storage.bat random-uuid` if needed, stores the ID in `config/kafka.env`,
and formats `kafka-runtime/data`. It safely recognizes already-formatted data
and refuses to reformat data with a different cluster ID.

The PowerShell setup uses Java directly with Kafka's libraries, which avoids
the space-in-path problem in Kafka's Windows batch launcher. It does not
require Bash, WSL, Docker, elevation, or a system-wide Kafka installation.
The existing `.sh` run/produce/consume wrappers are for Git Bash and can be
used after setup:

```bash
bash scripts/start-kafka.sh background
bash scripts/create-topic.sh
bash scripts/consume.sh
bash scripts/produce.sh
```

Run the consumer and producer in separate Git Bash windows. Enter messages in
the producer and press Ctrl+D when finished. Kafka listens on
`localhost:9092`; generated data and logs remain inside this project.

## Run Kafka

For Git Bash, run the one-time KRaft setup, then start the broker:

```bash
bash scripts/setup.sh
bash scripts/start-kafka.sh background
bash scripts/create-topic.sh
```

The setup generates a cluster ID with Kafka's `kafka-storage.sh random-uuid`,
saves it to `config/kafka.env`, and formats the project-local
`kafka-runtime/data` directory using `kafka/config/kraft/server.properties`.
The tracked `config/kraft/server.properties` is copied into the Kafka
distribution before formatting.
Run it as your normal user. It does not use ZooKeeper, Docker, elevated
privileges, or system directories. The start wrapper requires this setup to
have completed and does not format storage. Broker output is written to
`kafka-runtime/brokER-logs/kafka.log`.

Use `bash scripts/start-kafka.sh foreground` to keep the broker attached to the
terminal. The default mode is `background`; background start/stop messages are
written to the broker log.

Send a message and read messages in separate terminals:

```bash
bash scripts/produce.sh
bash scripts/consume.sh
```

The producer accepts messages interactively from stdin; press Ctrl+D when
finished. These wrappers use the `quickstart-events` topic, and their output
is appended to the corresponding file in `app-logs/`.

Inspect the broker using the available modes:

```bash
bash scripts/inspect.sh list
bash scripts/inspect.sh describe
bash scripts/inspect.sh tail
```

`describe` shows partition leaders/replicas and the latest log offsets. With
this single-replica broker, the latest offset is the HighWatermark.
`tail` follows broker logs along with the producer and consumer logs.

Stop the broker and tracked producer/consumer tools without removing data:

```bash
bash scripts/stop.sh
```

To also remove the contents of `kafka-runtime/` and `app-logs/`, pass the
explicit cleanup option:

```bash
bash scripts/stop.sh --clean
```

## Installation issues encountered and fixes

- **WSL was unavailable.** Git Bash was already installed, so WSL and Docker
  were not needed. Git Bash runs the project `.sh` wrappers; PowerShell can
  perform installation and KRaft formatting with `setup-kafka.ps1`.
- **Large archive downloads were interrupted.** The official Apache mirror
  initially failed during direct downloads. The Kafka 4.4.0 archive was
  ultimately downloaded in byte ranges and verified against Apache's SHA-512
  checksum. The PowerShell installer also verifies the official checksum
  before extracting a missing distribution.
- **Kafka reported “Classpath is empty” under Git Bash.** The project path
  contains a space (`secuity lending`). Kafka's bundled shell launcher used an
  unquoted `dirname $0`, which split that path and prevented its JAR lookup.
  Quoting the launcher path and using project-relative Kafka paths fixed the
  shell classpath lookup. The packaged Kafka launcher is under the ignored
  `kafka/` directory, so re-extracting Kafka replaces that local adjustment.
- **A direct broker API check failed with
  `ClassNotFoundException: lending.kafka.logs`.** Kafka was running; the
  direct command was missing the project-local logging environment, and its
  log path contained a space. The project startup wrapper now sets
  `LOG_DIR` and `KAFKA_LOG4J_OPTS` to project-relative paths. For a direct
  Git Bash CLI invocation, first load `config/kafka.env`.
- **Kafka's Windows `.bat` CLI also split classpath entries at the space.**
  The PowerShell setup avoids those batch launchers and invokes Kafka's
  `StorageTool` with Java's `kafka/libs/*` wildcard classpath, which was
  verified from this project path.
- **The broker API versions tool printed a deprecation warning.** The warning
  is informational; Kafka 4.4 recommends `kafka-cluster.sh api-versions`.
- **Verification succeeded.** KRaft formatting completed, the broker reached
  `RUNNING` on port 9092, the `quickstart-events` topic was created, and
  producer messages were received by the consumer.

## Full-loop verification

1. Complete one-time setup with `.\scripts\setup-kafka.ps1` in PowerShell, or
   `bash scripts/setup.sh` in Git Bash. Start the broker with
   `bash scripts/start-kafka.sh background`.
2. Create the topic: `bash scripts/create-topic.sh`.
3. Record the baseline offset with `bash scripts/inspect.sh describe`; note
   the `quickstart-events:0:<offset>` value under the latest-offset heading.
4. In a separate terminal, start `bash scripts/consume.sh`; it reads from the
   beginning and writes output to `app-logs/consumer.log`.
5. Run `bash scripts/produce.sh` and type a few distinct messages, then press
   Ctrl+D. The producer logs input to `app-logs/producer.log`.
6. Re-run `bash scripts/inspect.sh describe`. Confirm that the reported offset
   is greater than the baseline.
7. Run `bash scripts/inspect.sh tail` to follow the broker and application
   logs, or stop all tracked processes with `bash scripts/stop.sh`.

The default broker endpoint is `localhost:9092`, and the default topic is
`quickstart-events`. Adjust the broker endpoint and project-relative paths in
`config/kafka.env` as needed. Keep the cluster ID unchanged when reusing the
existing KRaft data; to create a different cluster, use a fresh data directory
and a newly generated Kafka cluster ID.
