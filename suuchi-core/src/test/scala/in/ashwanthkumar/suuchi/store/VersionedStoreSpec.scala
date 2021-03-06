package in.ashwanthkumar.suuchi.store

import java.nio.ByteBuffer

import in.ashwanthkumar.suuchi.store.PrimitivesSerDeUtils.{bytesToLong, longToBytes}
import in.ashwanthkumar.suuchi.utils.DateUtils
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

trait MockDateUtils extends DateUtils {
  var cnt = 0
  override def now: Long = {
    cnt += 1
    cnt
  }
}
class ByWriteTimestampMocked extends ByWriteTimestamp with MockDateUtils
class KeyAsVersion extends VersionedBy {
  override val versionOrdering: Ordering[Long] = Ordering.Long.reverse
  override def version(key: Array[Byte], value: Array[Byte]): Long = bytesToLong(key)
}

class VersionedStoreSpec extends FlatSpec {
  "VersionedStore" should "return no version info for a key for the first time" in {
    val store = new VersionedStore(new InMemoryStore, new ByWriteTimestampMocked, 3)
    store.getVersions(Array(1.toByte)).size should be(0)
  }

  it should "return version info appropriately after every insert" in {
    val store = new VersionedStore(new InMemoryStore, new ByWriteTimestampMocked, 3)
    store.getVersions(Array(1.toByte)).size should be(0)

    store.put(Array(1.toByte), Array(100.toByte))
    store.getVersions(Array(1.toByte)) should be(List(1))

    store.put(Array(1.toByte), Array(101.toByte))
    store.getVersions(Array(1.toByte)) should be(List(2, 1))

    store.put(Array(1.toByte), Array(102.toByte))
    store.getVersions(Array(1.toByte)) should be(List(3, 2, 1))

    store.put(Array(1.toByte), Array(103.toByte))
    store.getVersions(Array(1.toByte)) should be(List(4,3,2))
  }

  it should "write data for value with an earlier version" in {
    val store = new VersionedStore(new InMemoryStore, new KeyAsVersion, 3)
    store.put(longToBytes(456), longToBytes(456))
    store.getVersions(longToBytes(456)) should be(List(456))
    store.get(longToBytes(456)).map(ByteBuffer.wrap) should be(Some(ByteBuffer.wrap(longToBytes(456))))

    store.put(longToBytes(123), longToBytes(123))
    store.getVersions(longToBytes(123)) should be(List(123))
    store.get(longToBytes(123)).map(ByteBuffer.wrap) should be(Some(ByteBuffer.wrap(longToBytes(123))))
  }

  it should "delete old versions of data for a key when we exceed numVersions" in {
    val inMemoryStore = new InMemoryStore
    val store = new VersionedStore(inMemoryStore, new ByWriteTimestampMocked, 3)
    store.getVersions(Array(1.toByte)).size should be(0)

    store.put(Array(1.toByte), Array(100.toByte))
    store.getVersions(Array(1.toByte)) should be(List(1))

    store.put(Array(1.toByte), Array(101.toByte))
    store.getVersions(Array(1.toByte)) should be(List(2, 1))

    store.put(Array(1.toByte), Array(102.toByte))
    store.getVersions(Array(1.toByte)) should be(List(3, 2, 1))

    store.put(Array(1.toByte), Array(103.toByte))
    inMemoryStore.get(VersionedStore.dkey(Array(1.toByte), 1)) should be(None)
  }
}
