package one.ytb.state

import java.io.File
import java.util.concurrent.{ThreadLocalRandom, TimeUnit}

import com.typesafe.config.ConfigFactory
import one.ytb.account.{AddressOrAlias, AddressScheme, Alias}
import one.ytb.database.LevelDBWriter
import one.ytb.db.LevelDBFactory
import one.ytb.lang.v1.traits.Environment
import one.ytb.lang.v1.traits.domain.Recipient
import one.ytb.settings.{YtbSettings, loadConfig}
import one.ytb.state.YtbEnvironmentBenchmark._
import one.ytb.state.bench.DataTestData
import one.ytb.transaction.smart.YtbEnvironment
import one.mir.utils.Base58
import monix.eval.Coeval
import org.iq80.leveldb.{DB, Options}
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import scodec.bits.{BitVector, ByteVector}

import scala.io.Codec

/**
  * Tests over real database. How to test:
  * 1. Download a database
  * 2. Import it: https://github.com/raasakh/ytbnode/wiki/Export-and-import-of-the-blockchain#import-blocks-from-the-binary-file
  * 3. Run ExtractInfo to collect queries for tests
  * 4. Make Caches.MaxSize = 1
  * 5. Run this test
  */
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
@Threads(1)
@Fork(1)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
class YtbEnvironmentBenchmark {

  @Benchmark
  def resolveAddress_test(st: ResolveAddressSt, bh: Blackhole): Unit = {
    bh.consume(st.environment.resolveAlias(st.aliases.random))
  }

  @Benchmark
  def transactionById_test(st: TransactionByIdSt, bh: Blackhole): Unit = {
    bh.consume(st.environment.transactionById(st.allTxs.random))
  }

  @Benchmark
  def transactionHeightById_test(st: TransactionByIdSt, bh: Blackhole): Unit = {
    bh.consume(st.environment.transactionById(st.allTxs.random))
  }

  @Benchmark
  def accountBalanceOf_mir_test(st: AccountBalanceOfMirSt, bh: Blackhole): Unit = {
    bh.consume(st.environment.accountBalanceOf(Recipient.Address(ByteVector(st.accounts.random)), None))
  }

  @Benchmark
  def accountBalanceOf_asset_test(st: AccountBalanceOfAssetSt, bh: Blackhole): Unit = {
    bh.consume(st.environment.accountBalanceOf(Recipient.Address(ByteVector(st.accounts.random)), Some(st.assets.random)))
  }

  @Benchmark
  def data_test(st: DataSt, bh: Blackhole): Unit = {
    val x = st.data.random
    bh.consume(st.environment.data(Recipient.Address(x.addr), x.key, x.dataType))
  }

}

object YtbEnvironmentBenchmark {

  @State(Scope.Benchmark)
  class ResolveAddressSt extends BaseSt {
    val aliases: Vector[String] = load("resolveAddress", benchSettings.aliasesFile)(x => Alias.fromString(x).explicitGet().name)
  }

  @State(Scope.Benchmark)
  class TransactionByIdSt extends BaseSt {
    val allTxs: Vector[Array[Byte]] = load("transactionById", benchSettings.restTxsFile)(x => Base58.decode(x).get)
  }

  @State(Scope.Benchmark)
  class TransactionHeightByIdSt extends TransactionByIdSt

  @State(Scope.Benchmark)
  class AccountBalanceOfMirSt extends BaseSt {
    val accounts: Vector[Array[Byte]] = load("accounts", benchSettings.accountsFile)(x => AddressOrAlias.fromString(x).explicitGet().bytes.arr)
  }

  @State(Scope.Benchmark)
  class AccountBalanceOfAssetSt extends AccountBalanceOfMirSt {
    val assets: Vector[Array[Byte]] = load("assets", benchSettings.assetsFile)(x => Base58.decode(x).get)
  }

  @State(Scope.Benchmark)
  class DataSt extends BaseSt {
    val data: Vector[DataTestData] = load("data", benchSettings.dataFile) { line =>
      DataTestData.codec.decode(BitVector.fromBase64(line).get).require.value
    }
  }

  @State(Scope.Benchmark)
  class BaseSt {
    protected val benchSettings: Settings = Settings.fromConfig(ConfigFactory.load())
    private val ytbSettings: YtbSettings = {
      val config = loadConfig(ConfigFactory.parseFile(new File(benchSettings.networkConfigFile)))
      YtbSettings.fromConfig(config)
    }

    AddressScheme.current = new AddressScheme {
      override val chainId: Byte = ytbSettings.blockchainSettings.addressSchemeCharacter.toByte
    }

    private val db: DB = {
      val dir = new File(ytbSettings.dataDirectory)
      if (!dir.isDirectory) throw new IllegalArgumentException(s"Can't find directory at '${ytbSettings.dataDirectory}'")
      LevelDBFactory.factory.open(dir, new Options)
    }

    val environment: Environment = {
      val state = new LevelDBWriter(db, ytbSettings.blockchainSettings.functionalitySettings, 100000, 2000, 120 * 60 * 1000)
      new YtbEnvironment(
        AddressScheme.current.chainId,
        Coeval.raiseError(new NotImplementedError("tx is not implemented")),
        Coeval(state.height),
        state
      )
    }

    @TearDown
    def close(): Unit = {
      db.close()
    }

    protected def load[T](label: String, absolutePath: String)(f: String => T): Vector[T] = {
      scala.io.Source
        .fromFile(absolutePath)(Codec.UTF8)
        .getLines()
        .map(f)
        .toVector
    }
  }

  implicit class VectorOps[T](self: Vector[T]) {
    def random: T = self(ThreadLocalRandom.current().nextInt(self.size))
  }

}
