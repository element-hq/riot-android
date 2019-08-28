package im.vector.util

import androidx.test.InstrumentationRegistry
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream


class SecureSecretStoreTest {

    @Test
    fun test_symetric_encrypt_decrypt() {
        val clearText = "Hello word!"
        val encrypted = SecretStoringUtils.encryptStringM(clearText, "foo")
        Assert.assertNotNull(encrypted)
        val decrypted = SecretStoringUtils.decryptStringM(encrypted!!, "foo");

        Assert.assertEquals(clearText, decrypted)

        //Test kitkat way
        val context = InstrumentationRegistry.getContext()
        val KEncrypted = SecretStoringUtils.encryptStringJ(clearText, "bar", context)

        val kDecripted = SecretStoringUtils.decryptStringJ(KEncrypted!!, "bar", context)

        Assert.assertEquals(clearText, kDecripted)
    }

    @Test
    fun test_symetric_encrypt_decrypt_kitkat() {

        val clearText = "Hello word!"
        //Test kitkat way
        val context = InstrumentationRegistry.getContext()
        val KEncrypted = SecretStoringUtils.encryptStringJ(clearText, "bar", context)
        Assert.assertNotNull("Failed to encrypt", KEncrypted)
        Assert.assertTrue(KEncrypted!![0] == SecretStoringUtils.FORMAT_1)
        val kDecripted = SecretStoringUtils.decryptStringJ(KEncrypted!!, "bar", context)

        Assert.assertEquals(clearText, kDecripted)
    }

    @Test
    fun test_symetric_encrypt_decrypt_veryold_device() {

        val clearText = "Hello word!"
        //Test kitkat way
        val KEncrypted = SecretStoringUtils.encryptForOldDevicesNotGood(clearText, "bar")
        Assert.assertNotNull("Failed to encrypt", KEncrypted)
        Assert.assertTrue(KEncrypted!![0] == SecretStoringUtils.FORMAT_2)
        val kDecripted = SecretStoringUtils.decryptForOldDevicesNotGood(KEncrypted!!, "bar")

        Assert.assertEquals(clearText, kDecripted)
    }

    @Test
    fun test_store_secret() {

        val clearText = "Hello word!"
        //Test kitkat way
        val context = InstrumentationRegistry.getContext()
        val KEncrypted = SecretStoringUtils.securelyStoreString(clearText, "bar", context)
        Assert.assertNotNull("Failed to encrypt", KEncrypted)
        val kDecripted = SecretStoringUtils.loadSecureSecret(KEncrypted!!, "bar", context)

        Assert.assertEquals(clearText, kDecripted)
    }


    @Test
    fun test_secureObjectStoreM() {

        val data = listOf<String>("Store", "this", "encrypted", "Please")

        val bos = ByteArrayOutputStream()
        SecretStoringUtils.saveSecureObjectM("bar1", bos, data)
        val bytes = bos.toByteArray()
        Assert.assertTrue(bytes.size > 0)

        val bis = ByteArrayInputStream(bytes)
        val loadedData = SecretStoringUtils.loadSecureObjectM<List<String>>("bar1", bis)
        Assert.assertNotNull(loadedData)
        Assert.assertEquals(data.size, loadedData!!.size)
        for (i in 0..data.size - 1) {
            Assert.assertEquals(data[i], loadedData!![i])
            System.out.print(loadedData!![i])
        }
    }

    @Test
    fun test_secureObjectStore_kitkat() {

        val context = InstrumentationRegistry.getContext()
        val data = listOf<String>("Store", "this", "encrypted", "Please")

        val bos = ByteArrayOutputStream()
        SecretStoringUtils.saveSecureObjectK("bar1", bos, data, context)
        val bytes = bos.toByteArray()
        Assert.assertTrue(bytes.size > 0)

        val bis = ByteArrayInputStream(bytes)
        val loadedData = SecretStoringUtils.loadSecureObjectK<List<String>>("bar1", bis, context)
        Assert.assertNotNull(loadedData)
        Assert.assertEquals(data.size, loadedData!!.size)
        for (i in 0..data.size - 1) {
            Assert.assertEquals(data[i], loadedData!![i])
            System.out.print(loadedData!![i])
        }
    }

    @Test
    fun test_secureObjectStore_older() {

        val data = listOf<String>("Store", "this", "encrypted", "Please")

        val bos = ByteArrayOutputStream()
        SecretStoringUtils.saveSecureObjectOldNotGood("bar1", bos, data)
        val bytes = bos.toByteArray()
        Assert.assertTrue(bytes.size > 0)

        Assert.assertEquals("Wrong Format", SecretStoringUtils.FORMAT_2, bytes[0])

        val bis = ByteArrayInputStream(bytes)
        val loadedData = SecretStoringUtils.loadSecureObjectOldNotGood<List<String>>("bar1", bis)
        Assert.assertNotNull(loadedData)
        Assert.assertEquals(data.size, loadedData!!.size)
        for (i in 0..data.size - 1) {
            Assert.assertEquals(data[i], loadedData!![i])
            System.out.print(loadedData!![i])
        }
    }

    @Test
    fun test_secureObjectStore() {

        val context = InstrumentationRegistry.getContext()
        val data = listOf<String>("Store", "this", "encrypted", "Please")

        val bos = ByteArrayOutputStream()
        SecretStoringUtils.securelyStoreObject(data, "bar1", bos, context)
        val bytes = bos.toByteArray()
        Assert.assertTrue(bytes.size > 0)

        val bis = ByteArrayInputStream(bytes)
        val loadedData = SecretStoringUtils.loadSecureSecret<List<String>>(bis, "bar1", context)
        Assert.assertNotNull(loadedData)
        Assert.assertEquals(data.size, loadedData!!.size)
        for (i in 0..data.size - 1) {
            Assert.assertEquals(data[i], loadedData!![i])
            System.out.print(loadedData!![i])
        }
    }
}