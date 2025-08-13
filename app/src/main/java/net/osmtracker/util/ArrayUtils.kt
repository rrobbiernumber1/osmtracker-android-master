package net.osmtracker.util

object ArrayUtils {

	@JvmStatic
	fun findMin(`in`: Array<DoubleArray>, offset: Int): Double {
		var out = `in`[0][offset]
		for (i in `in`.indices) {
			if (`in`[i][offset] < out) {
				out = `in`[i][offset]
			}
		}
		return out
	}

	@JvmStatic
	fun findMax(`in`: Array<DoubleArray>, offset: Int): Double {
		var out = `in`[0][offset]
		for (i in `in`.indices) {
			if (`in`[i][offset] > out) {
				out = `in`[i][offset]
			}
		}
		return out
	}
}


