package ng.ereach.facilityserver.iam.services;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

/**
 * Draws a provisioning URI as a QR code, sent as a {@code data:image/png;base64,} image that the client puts
 * straight into an image tag (D-027, transcript of 01/11/2026). The image holds the secret, so it is never logged.
 */
public final class AuthenticatorQrCode {

	private static final int SIZE_PIXELS = 256;
	private static final int QUIET_ZONE_MODULES = 4;
	private static final String DATA_URI_PREFIX = "data:image/png;base64,";

	private AuthenticatorQrCode() {
	}

	public static String toPngDataUri(String provisioningUri) {
		BitMatrix matrix;
		try {
			matrix = new QRCodeWriter().encode(provisioningUri, BarcodeFormat.QR_CODE, SIZE_PIXELS, SIZE_PIXELS,
					Map.of(EncodeHintType.MARGIN, QUIET_ZONE_MODULES, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
		} catch (WriterException e) {
			throw new IllegalStateException("Could not draw the authenticator QR code", e);
		}

		BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < matrix.getHeight(); y++) {
			for (int x = 0; x < matrix.getWidth(); x++) {
				image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
			}
		}

		ByteArrayOutputStream png = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "png", png);
		} catch (IOException e) {
			throw new IllegalStateException("Could not write the authenticator QR code", e);
		}
		return DATA_URI_PREFIX + Base64.getEncoder().encodeToString(png.toByteArray());
	}
}
