package cn.neeto.stockpot.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.net.QrMatrix
import cn.neeto.stockpot.ui.theme.LocalPotColors

/**
 * 圆形头像：有图就铺图，没图就把名字的第一个字写在品牌淡色圆里。
 *
 * 头像不下载失败、没设过、换了账号，都稳稳落在「一个字」上——比一个灰色剪影更容易认人。
 */
@Composable
fun Avatar(
    bitmap: Bitmap?,
    initial: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPotColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.brandTint),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initial,
                // 字号跟着圆走：32dp 的小头像用 13sp，72dp 的大头像用 28sp
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Medium,
                color = colors.brandInk,
            )
        }
    }
}

/**
 * 两步验证的二维码。
 *
 * 服务端把 `otpauth://` 编成模块矩阵发下来，这里一格一格画——不引图片解码库。
 * 底色固定白、块固定黑：扫码器要的是对比度，跟着深色主题翻过来就扫不动了。
 */
@Composable
fun QrCode(matrix: QrMatrix, size: Dp, modifier: Modifier = Modifier) {
    if (matrix.size <= 0 || matrix.rows.isEmpty()) return
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(10.dp),
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val cell = this.size.minDimension / matrix.size
            matrix.rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, mark ->
                    if (mark == '1') {
                        drawRect(
                            color = Color(0xFF14110F),
                            topLeft = Offset(x * cell, y * cell),
                            size = Size(cell + 0.4f, cell + 0.4f),
                        )
                    }
                }
            }
        }
    }
}
