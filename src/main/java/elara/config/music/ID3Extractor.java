package elara.config.music;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;

public class ID3Extractor {
   public static ID3Extractor.ID3Data extract(File mp3File) {
      ID3Extractor.ID3Data data = new ID3Extractor.ID3Data();

      try {
         FileInputStream fis = new FileInputStream(mp3File);

         ID3Extractor.ID3Data var18;
         label165: {
            label164: {
               label163: {
                  ID3Extractor.ID3Data var19;
                  label162: {
                     ID3Extractor.ID3Data var20;
                     label161: {
                        ID3Extractor.ID3Data var21;
                        label160: {
                           ID3Extractor.ID3Data var24;
                           label159: {
                              label158: {
                                 ID3Extractor.ID3Data var22;
                                 try {
                                    byte[] magic = new byte[4];
                                    if (fis.read(magic) != 4) {
                                       var18 = data;
                                       break label165;
                                    }

                                    if (magic[0] == 102 && magic[1] == 76 && magic[2] == 97 && magic[3] == 67) {
                                       var18 = extractFlac(fis, data);
                                       break label164;
                                    }

                                    if (magic[0] != 73 || magic[1] != 68 || magic[2] != 51) {
                                       var18 = data;
                                       break label163;
                                    }

                                    int majorVersion = magic[3] & 255;
                                    byte[] rest = new byte[6];
                                    if (fis.read(rest) != 6) {
                                       var19 = data;
                                       break label162;
                                    }

                                    int size = syncSafeInt(rest, 2);
                                    if (size <= 0) {
                                       var20 = data;
                                       break label161;
                                    }

                                    byte[] tagData = new byte[size];
                                    if (fis.read(tagData) != size) {
                                       var21 = data;
                                       break label160;
                                    }

                                    int pos = 0;

                                    while (pos < size - 10) {
                                       String frameId = new String(tagData, pos, 4);
                                       int frameSize = majorVersion == 4 ? syncSafeInt(tagData, pos + 4) : bigEndianInt(tagData, pos + 4);
                                       int frameFlags = bigEndianShort(tagData, pos + 8);
                                       if (frameSize <= 0) {
                                          var24 = data;
                                          break label159;
                                       }

                                       if (pos + 10 + frameSize > size) {
                                          var24 = data;
                                          break label158;
                                       }

                                       byte[] frameData = new byte[frameSize];
                                       System.arraycopy(tagData, pos + 10, frameData, 0, frameSize);
                                       if (!"APIC".equals(frameId) && (frameId.length() != 3 || !frameId.startsWith("PIC"))) {
                                          if ("TIT2".equals(frameId) || "TT2".equals(frameId)) {
                                             data.title = parseTextFrame(frameData);
                                          } else if ("TPE1".equals(frameId) || "TP1".equals(frameId)) {
                                             data.artist = parseTextFrame(frameData);
                                          } else if ("TALB".equals(frameId) || "TAL".equals(frameId)) {
                                             data.album = parseTextFrame(frameData);
                                          }
                                       } else {
                                          parseApicFrame(frameData, data);
                                       }

                                       pos += 10 + frameSize;
                                    }

                                    var22 = data;
                                 } catch (Throwable var14) {
                                    try {
                                       fis.close();
                                    } catch (Throwable var13) {
                                       var14.addSuppressed(var13);
                                    }

                                    throw var14;
                                 }

                                 fis.close();
                                 return var22;
                              }

                              fis.close();
                              return var24;
                           }

                           fis.close();
                           return var24;
                        }

                        fis.close();
                        return var21;
                     }

                     fis.close();
                     return var20;
                  }

                  fis.close();
                  return var19;
               }

               fis.close();
               return var18;
            }

            fis.close();
            return var18;
         }

         fis.close();
         return var18;
      } catch (IOException var15) {
         return data;
      }
   }

   private static ID3Extractor.ID3Data extractFlac(FileInputStream fis, ID3Extractor.ID3Data data) {
      try {
         boolean lastBlock = false;

         while (!lastBlock) {
            int b0 = fis.read();
            int b1 = fis.read();
            int b2 = fis.read();
            int b3 = fis.read();
            if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) {
               break;
            }

            lastBlock = (b0 & 128) != 0;
            int type = b0 & 127;
            int length = (b1 & 0xFF) << 16 | (b2 & 0xFF) << 8 | b3 & 0xFF;
            if (length < 0) {
               break;
            }

            byte[] blockData = new byte[length];
            if (length > 0 && readFully(fis, blockData) != length) {
               break;
            }

            if (type == 4) {
               parseVorbisComment(blockData, data);
            } else if (type == 6) {
               parseFlacPicture(blockData, data);
            }
         }
      } catch (IOException var10) {
      }

      return data;
   }

   private static void parseVorbisComment(byte[] b, ID3Extractor.ID3Data data) {
      int pos = 0;
      if (pos + 4 <= b.length) {
         int vendorLen = readLEInt(b, pos);
         pos += 4;
         if (vendorLen >= 0 && pos + vendorLen <= b.length) {
            pos += vendorLen;
            if (pos + 4 <= b.length) {
               int count = readLEInt(b, pos);
               pos += 4;

               for (int i = 0; i < count && pos + 4 <= b.length; i++) {
                  int clen = readLEInt(b, pos);
                  pos += 4;
                  if (clen < 0 || pos + clen > b.length) {
                     break;
                  }

                  String comment;
                  try {
                     comment = new String(b, pos, clen, "UTF-8");
                  } catch (Exception e) {
                     pos += clen;
                     continue;
                  }

                  pos += clen;
                  int eq = comment.indexOf(61);
                  if (eq > 0) {
                     String field = comment.substring(0, eq).toUpperCase();
                     String value = comment.substring(eq + 1);
                     if (!field.equals("TITLE") || data.title != null && !data.title.isEmpty()) {
                        if (!field.equals("ARTIST") || data.artist != null && !data.artist.isEmpty()) {
                           if (!field.equals("ALBUM") || data.album != null && !data.album.isEmpty()) {
                              if (field.equals("METADATA_BLOCK_PICTURE") && data.coverImage == null) {
                                 decodeMetadataBlockPicture(value, data);
                              }
                           } else {
                              data.album = value;
                           }
                        } else {
                           data.artist = value;
                        }
                     } else {
                        data.title = value;
                     }
                  }
               }
            }
         }
      }
   }

   private static void parseFlacPicture(byte[] b, ID3Extractor.ID3Data data) {
      if (data.coverImage == null) {
         try {
            int pos = 0;
            if (pos + 4 > b.length) {
               return;
            }

            pos += 4;
            if (pos + 4 > b.length) {
               return;
            }

            int mimeLen = bigEndianInt(b, pos);
            pos += 4;
            if (mimeLen < 0 || pos + mimeLen > b.length) {
               return;
            }

            String mime = new String(b, pos, mimeLen, "ASCII");
            pos += mimeLen;
            if (pos + 4 > b.length) {
               return;
            }

            int descLen = bigEndianInt(b, pos);
            pos += 4;
            if (descLen < 0 || pos + descLen > b.length) {
               return;
            }

            pos += descLen;
            pos += 16;
            if (pos + 4 > b.length) {
               return;
            }

            int dataLen = bigEndianInt(b, pos);
            pos += 4;
            if (dataLen <= 0 || pos + dataLen > b.length) {
               return;
            }

            byte[] img = new byte[dataLen];
            System.arraycopy(b, pos, img, 0, dataLen);
            data.coverImage = img;
            data.coverMime = mime != null ? mime : "";
         } catch (Exception var8) {
         }
      }
   }

   private static void decodeMetadataBlockPicture(String base64, ID3Extractor.ID3Data data) {
      try {
         byte[] pic = Base64.getDecoder().decode(base64);
         parseFlacPicture(pic, data);
      } catch (Exception var3) {
      }
   }

   private static int readLEInt(byte[] b, int off) {
      return b[off] & 0xFF | (b[off + 1] & 0xFF) << 8 | (b[off + 2] & 0xFF) << 16 | (b[off + 3] & 0xFF) << 24;
   }

   private static int readFully(FileInputStream fis, byte[] buf) throws IOException {
      int total = 0;

      while (total < buf.length) {
         int n = fis.read(buf, total, buf.length - total);
         if (n < 0) {
            break;
         }

         total += n;
      }

      return total;
   }

   private static void parseApicFrame(byte[] data, ID3Extractor.ID3Data out) {
      if (data.length >= 5) {
         int encoding = data[0] & 255;
         StringBuilder mime = new StringBuilder();

         int pos;
         for (pos = 1; pos < data.length && data[pos] != 0; pos++) {
            mime.append((char)(data[pos] & 0xFF));
         }

         out.coverMime = mime.toString();
         if (++pos < data.length) {
            pos++;
         }

         if ((pos = findNullTerminator(data, pos, encoding) + (encoding != 1 && encoding != 2 ? 1 : 2)) < data.length) {
            int imgLen = data.length - pos;
            out.coverImage = new byte[imgLen];
            System.arraycopy(data, pos, out.coverImage, 0, imgLen);
         }
      }
   }

   private static int findNullTerminator(byte[] data, int start, int encoding) {
      if (encoding != 1 && encoding != 2) {
         for (int i = start; i < data.length; i++) {
            if (data[i] == 0) {
               return i;
            }
         }
      } else {
         for (int i = start; i < data.length - 1; i += 2) {
            if (data[i] == 0 && data[i + 1] == 0) {
               return i;
            }
         }
      }

      return data.length - 1;
   }

   private static String parseTextFrame(byte[] data) {
      if (data.length < 2) {
         return "";
      }

      int encoding = data[0] & 255;

      try {
         if (encoding == 1 || encoding == 2) {
            // UTF-16: 按 2 字节对齐去掉尾部 null terminator (00 00)
            int len = data.length - 1;
            // 确保长度为偶数
            if (len % 2 != 0) len--;
            // 去掉尾部 00 00
            while (len >= 2 && data[len - 1] == 0 && data[len] == 0) {
                len -= 2;
            }
            if (len <= 0) return "";
            String charset = encoding == 1 ? "UTF-16" : "UTF-16BE";
            return new String(data, 1, len, charset);
         } else if (encoding == 3) {
            // UTF-8
            int len = data.length - 1;
            while (len > 0 && data[len] == 0) len--;
            if (len <= 0) return "";
            return new String(data, 1, len, "UTF-8");
         } else {
            // encoding == 0: ISO-8859-1 / Latin-1
            // 中文 MP3 常用 GBK 编码但标记为 encoding=0，尝试 GBK fallback
            int len = data.length - 1;
            while (len > 0 && data[len] == 0) len--;
            if (len <= 0) return "";
            // 先尝试 GBK（中文歌曲最常见）
            try {
               String gbk = new String(data, 1, len, "GBK");
               // 检查是否有乱码特征（替换字符）
               if (!gbk.contains("\uFFFD") && isReadableText(gbk)) {
                  return gbk;
               }
            } catch (Exception ignored) {}
            // 回退到 ISO-8859-1
            return new String(data, 1, len, "ISO-8859-1");
         }
      } catch (Exception e) {
         return "";
      }
   }

   private static boolean isReadableText(String s) {
      if (s == null || s.isEmpty()) return false;
      int printable = 0;
      for (char c : s.toCharArray()) {
         if (c >= 0x20 && c != 0x7F || c >= 0x4E00 && c <= 0x9FFF // CJK
            || c >= 0x3040 && c <= 0x30FF // 日文
            || c >= 0xAC00 && c <= 0xD7AF) { // 韩文
            printable++;
         }
      }
      return printable >= s.length() * 0.7;
   }

   private static int syncSafeInt(byte[] data, int offset) {
      return (data[offset] & 127) << 21 | (data[offset + 1] & 127) << 14 | (data[offset + 2] & 127) << 7 | data[offset + 3] & 127;
   }

   private static int bigEndianInt(byte[] data, int offset) {
      return (data[offset] & 0xFF) << 24 | (data[offset + 1] & 0xFF) << 16 | (data[offset + 2] & 0xFF) << 8 | data[offset + 3] & 0xFF;
   }

   private static int bigEndianShort(byte[] data, int offset) {
      return (data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF;
   }

   public static class ID3Data {
      public String title = "";
      public String artist = "";
      public String album = "";
      public byte[] coverImage = null;
      public String coverMime = "";
   }
}
