package io.nekohasekai.sagernet.database

import android.database.sqlite.SQLiteCantOpenDatabaseException
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import java.io.IOException
import java.sql.SQLException


object ProfileManager {

    interface Listener {
        suspend fun onAdd(profile: ProxyEntity)
        suspend fun onUpdated(data: TrafficData)
        suspend fun onUpdated(profile: ProxyEntity, noTraffic: Boolean)
        suspend fun onRemoved(groupId: Long, profileId: Long)
    }

    interface RuleListener {
        suspend fun onAdd(rule: RuleEntity)
        suspend fun onUpdated(rule: RuleEntity)
        suspend fun onRemoved(ruleId: Long)
        suspend fun onCleared()
    }

    private val listeners = ArrayList<Listener>()
    private val ruleListeners = ArrayList<RuleListener>()

    suspend fun iterator(what: suspend Listener.() -> Unit) {
        synchronized(listeners) {
            listeners.toList()
        }.forEach { listener ->
            what(listener)
        }
    }

    suspend fun ruleIterator(what: suspend RuleListener.() -> Unit) {
        val ruleListeners = synchronized(ruleListeners) {
            ruleListeners.toList()
        }
        for (listener in ruleListeners) {
            what(listener)
        }
    }

    fun addListener(listener: Listener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: Listener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun addListener(listener: RuleListener) {
        synchronized(ruleListeners) {
            ruleListeners.add(listener)
        }
    }

    fun removeListener(listener: RuleListener) {
        synchronized(ruleListeners) {
            ruleListeners.remove(listener)
        }
    }

    suspend fun createProfile(groupId: Long, bean: AbstractBean): ProxyEntity {
        bean.applyDefaultValues()

        val profile = ProxyEntity(groupId = groupId).apply {
            id = 0
            putBean(bean)
            userOrder = SagerDatabase.proxyDao.nextOrder(groupId) ?: 1
        }
        profile.id = SagerDatabase.proxyDao.addProxy(profile)
        iterator { onAdd(profile) }
        return profile
    }

    suspend fun updateProfile(profile: ProxyEntity) {
        SagerDatabase.proxyDao.updateProxy(profile)
        iterator { onUpdated(profile, false) }
    }

    suspend fun updateProfile(profiles: List<ProxyEntity>) {
        SagerDatabase.proxyDao.updateProxy(profiles)
        profiles.forEach {
            iterator { onUpdated(it, false) }
        }
    }

    suspend fun deleteProfile2(groupId: Long, profileId: Long) {
        if (SagerDatabase.proxyDao.deleteById(profileId) == 0) return
        if (DataStore.selectedProxy == profileId) {
            DataStore.selectedProxy = 0L
        }
    }

    suspend fun deleteProfile(groupId: Long, profileId: Long) {
        if (SagerDatabase.proxyDao.deleteById(profileId) == 0) return
        if (DataStore.selectedProxy == profileId) {
            DataStore.selectedProxy = 0L
        }
        iterator { onRemoved(groupId, profileId) }
        if (SagerDatabase.proxyDao.countByGroup(groupId) > 1) {
            GroupManager.rearrange(groupId)
        }
    }

    fun getProfile(profileId: Long): ProxyEntity? {
        if (profileId == 0L) return null
        return try {
            SagerDatabase.proxyDao.getById(profileId)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            throw IOException(ex)
        } catch (ex: SQLException) {
            Logs.w(ex)
            null
        }
    }

    fun getProfiles(profileIds: List<Long>): List<ProxyEntity> {
        if (profileIds.isEmpty()) return listOf()
        return try {
            SagerDatabase.proxyDao.getEntities(profileIds)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            throw IOException(ex)
        } catch (ex: SQLException) {
            Logs.w(ex)
            listOf()
        }
    }

    // postUpdate: post to listeners, don't change the DB

    suspend fun postUpdate(profileId: Long, noTraffic: Boolean = false) {
        postUpdate(getProfile(profileId) ?: return, noTraffic)
    }

    suspend fun postUpdate(profile: ProxyEntity, noTraffic: Boolean = false) {
        iterator { onUpdated(profile, noTraffic) }
    }

    suspend fun postUpdate(data: TrafficData) {
        iterator { onUpdated(data) }
    }

    suspend fun createRule(rule: RuleEntity, post: Boolean = true): RuleEntity {
        rule.userOrder = SagerDatabase.rulesDao.nextOrder() ?: 1
        rule.id = SagerDatabase.rulesDao.createRule(rule)
        if (post) {
            ruleIterator { onAdd(rule) }
        }
        return rule
    }

    suspend fun updateRule(rule: RuleEntity) {
        SagerDatabase.rulesDao.updateRule(rule)
        ruleIterator { onUpdated(rule) }
    }

    suspend fun deleteRule(ruleId: Long) {
        SagerDatabase.rulesDao.deleteById(ruleId)
        ruleIterator { onRemoved(ruleId) }
    }

    suspend fun deleteRules(rules: List<RuleEntity>) {
        SagerDatabase.rulesDao.deleteRules(rules)
        ruleIterator {
            rules.forEach {
                onRemoved(it.id)
            }
        }
    }

    @Synchronized
    fun ensureDefaultRules() {
        val rules = SagerDatabase.rulesDao.allRules()
        if (rules.isEmpty() && !DataStore.rulesFirstCreate) {
            DataStore.rulesFirstCreate = true
            val defaultRules = listOf(
                RuleEntity(
                    name = "屏蔽 QUIC (强制TCP+IPv6轮询)",
                    userOrder = 1L,
                    enabled = true,
                    port = "443",
                    network = "udp",
                    protocol = "quic",
                    outbound = -2L
                ),
                RuleEntity(
                    name = "屏蔽 UDP 443 (防QUIC复用)",
                    userOrder = 2L,
                    enabled = true,
                    port = "443",
                    network = "udp",
                    outbound = -2L
                ),
                RuleEntity(
                    name = "屏蔽 WebRTC STUN (防真实IP泄露)",
                    userOrder = 3L,
                    enabled = true,
                    port = "3478,5349,19302,19305",
                    network = "udp",
                    outbound = -2L
                ),
                RuleEntity(
                    name = "Google 全家桶强制代理 (geosite+域名)",
                    userOrder = 4L,
                    enabled = true,
                    domains = "geosite:google\ndomain:google.com\ndomain:googleapis.com\ndomain:googleapis.cn\ndomain:gstatic.com\ndomain:googleusercontent.com\ndomain:accounts.google.com\ndomain:1e100.net\ndomain:ggpht.com\ndomain:gvt1.com\ndomain:gvt2.com\ndomain:youtube.com\ndomain:ytimg.com\ndomain:googlevideo.com\ndomain:gmail.com\ndomain:android.com\ndomain:g.co",
                    outbound = 0L
                ),
                RuleEntity(
                    name = app.getString(R.string.route_play_store, "中国"),
                    userOrder = 5L,
                    enabled = true,
                    domains = "domain:googleapis.cn\ndomain:xn--ngstr-lra8j.com",
                    outbound = 0L
                ),
                RuleEntity(
                    name = app.getString(R.string.route_opt_block_ads),
                    userOrder = 6L,
                    enabled = false,
                    domains = "geosite:category-ads-all",
                    outbound = -2L
                ),
                RuleEntity(
                    name = app.getString(R.string.route_bypass_domain, "中国"),
                    userOrder = 7L,
                    enabled = false,
                    domains = "geosite:cn",
                    outbound = -1L
                ),
                RuleEntity(
                    name = app.getString(R.string.route_bypass_ip, "中国"),
                    userOrder = 8L,
                    enabled = false,
                    ip = "geoip:cn",
                    outbound = -1L
                )
            )
            defaultRules.forEach { rule ->
                rule.id = SagerDatabase.rulesDao.createRule(rule)
            }
        }
    }

    suspend fun getRules(): List<RuleEntity> {
        ensureDefaultRules()
        return SagerDatabase.rulesDao.allRules()
    }

}
