package com.motofbt.app

import kotlin.math.sqrt

data class P3(val x: Float, val y: Float, val z: Float)

class TrackerSolver {
    private val last = HashMap<Int, P3>()
    var smoothing = 0.42f
    var maxStep = 0.16f
    private fun smooth(id: Int, raw: P3): P3 {
        val old=last[id] ?: return raw.also { last[id]=it }
        val d=sqrt((raw.x-old.x)*(raw.x-old.x)+(raw.y-old.y)*(raw.y-old.y)+(raw.z-old.z)*(raw.z-old.z))
        val a=(smoothing + (d*1.8f).coerceIn(0f,0.38f)).coerceIn(0.12f,0.8f)
        val nx=old.x+(raw.x-old.x).coerceIn(-maxStep,maxStep)*a
        val ny=old.y+(raw.y-old.y).coerceIn(-maxStep,maxStep)*a
        val nz=old.z+(raw.z-old.z).coerceIn(-maxStep,maxStep)*a
        return P3(nx,ny,nz).also { last[id]=it }
    }
    fun solve(l: List<P3>): Map<Int,P3> {
        if (l.size < 33) return emptyMap()
        val hip=P3((l[23].x+l[24].x)/2f,(l[23].y+l[24].y)/2f,(l[23].z+l[24].z)/2f)
        fun rel(p:P3)=P3((p.x-hip.x)*2.0f,(hip.y-p.y)*2.0f,(p.z-hip.z)*2.0f)
        val out=HashMap<Int,P3>()
        val map=mapOf(1 to 27, 2 to 28, 3 to 25, 4 to 26, 7 to 13, 8 to 14)
        for ((tid,idx) in map) out[tid]=smooth(tid,rel(l[idx]))
        out[5]=smooth(5,P3(0f,0f,0f))
        return out
    }
}
