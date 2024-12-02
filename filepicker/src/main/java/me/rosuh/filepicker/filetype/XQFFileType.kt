package me.rosuh.filepicker.filetype

import me.rosuh.filepicker.R

/**
 *
 * 数据库 文件类型
 * @author rosu
 * @date 2018/11/27
 */
class XQFFileType : FileType {

    override val fileType: String
        get() = "XQF"
    override val fileIconResId: Int
        get() = R.drawable.application_xiangqi_xqf

    override fun verify(fileName: String): Boolean {
        /**
         * 使用 endWith 是不可靠的，因为文件名有可能是以格式结尾，但是没有 . 符号
         * 比如 文件名仅为：example_png
         */
        val isHasSuffix = fileName.contains(".")
        if (!isHasSuffix) {
            // 如果没有 . 符号，即是没有文件后缀
            return false
        }
        val suffix = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase()
        return when (suffix) {
            "xqf" -> {
                true
            }
            else -> {
                false
            }
        }
    }
}