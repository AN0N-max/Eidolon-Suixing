package com.eidolon.sc.mobile;
import org.json.*;import java.util.Base64;import java.nio.charset.StandardCharsets;import org.bouncycastle.crypto.util.PrivateKeyFactory;import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;import org.bouncycastle.crypto.signers.Ed25519Signer;
/** RFC8410 PKCS8 + Ed25519 lightweight signer; no replacement of Android crypto providers. */
final class QWeatherSigner {
 static String b64(byte[] b){return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 static String sign(String pem,String kid,String developer,String project,long now)throws Exception{
  if(kid.isEmpty()||developer.isEmpty()||project.isEmpty())throw new Exception("请填写和风凭据ID、开发者ID和项目ID");String clean=pem.replace("\\n","\n").replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replaceAll("\\s","");
  org.bouncycastle.crypto.params.AsymmetricKeyParameter key=PrivateKeyFactory.createKey(Base64.getDecoder().decode(clean));if(!(key instanceof Ed25519PrivateKeyParameters))throw new Exception("需要Ed25519 PKCS8私钥");long iat=now/1000-30;
  String data=b64(new JSONObject().put("alg","EdDSA").put("kid",kid).toString().getBytes(StandardCharsets.UTF_8))+"."+b64(new JSONObject().put("iss",developer).put("sub",project).put("iat",iat).put("exp",iat+900).toString().getBytes(StandardCharsets.UTF_8));Ed25519Signer signer=new Ed25519Signer();signer.init(true,key);byte[] bytes=data.getBytes(StandardCharsets.UTF_8);signer.update(bytes,0,bytes.length);return data+"."+b64(signer.generateSignature());
 }
}
