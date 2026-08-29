package com.xfqiu.floatball.core

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupManifestTest {

    @Test
    fun manifestDeclaresBootAndUpgradeRecovery() {
        val manifest = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).first { it.isFile }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)

        val permissions = document.getElementsByTagName("uses-permission").asAttributeSet("android:name")
        assertTrue("缺少 RECEIVE_BOOT_COMPLETED", permissions.contains("android.permission.RECEIVE_BOOT_COMPLETED"))

        val receivers = document.getElementsByTagName("receiver")
        val receiver = (0 until receivers.length)
            .map { receivers.item(it) }
            .first { it.attributes.getNamedItem("android:name")?.nodeValue == ".receiver.StartupReceiver" }
        val actions = receiver.childNodes
            .let { children ->
                (0 until children.length)
                    .map { children.item(it) }
                    .flatMap { node ->
                        val descendants = node.childNodes
                        (0 until descendants.length).map { descendants.item(it) }
                    }
                    .filter { it.nodeName == "action" }
                    .mapNotNull { it.attributes?.getNamedItem("android:name")?.nodeValue }
                    .toSet()
            }

        assertTrue(actions.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue(actions.contains("android.intent.action.MY_PACKAGE_REPLACED"))
    }
}

private fun org.w3c.dom.NodeList.asAttributeSet(attribute: String): Set<String> =
    (0 until length).mapNotNull { item(it).attributes?.getNamedItem(attribute)?.nodeValue }.toSet()
