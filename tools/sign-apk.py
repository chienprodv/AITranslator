"""Sign an unsigned CI APK using a retained local key. Never upload the key directory."""
import argparse,hashlib,json,os,secrets,subprocess
from pathlib import Path

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--input',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
p.add_argument('--key-dir',type=Path,required=True);p.add_argument('--apksigner',type=Path,required=True)
p.add_argument('--create-key',action='store_true',help='Only for the first signing identity; cannot recreate a lost identity')
a=p.parse_args();a.key_dir.mkdir(parents=True,exist_ok=True)
key=a.key_dir/'XinayuAITranslator-debug.p12';password=a.key_dir/'store-password.txt';identity=a.key_dir/'identity.json'
if not a.input.is_file() or not a.apksigner.is_file():raise SystemExit('APK or official apksigner.jar is missing')
if a.input.resolve()==a.output.resolve():raise SystemExit('Use a separate output path')
if not key.exists() or not password.exists():
 if not a.create_key or key.exists() or identity.exists():
  raise SystemExit('Signing key missing/incomplete. Restore the original key directory; do not silently generate another key.')
 if not password.exists():password.write_text(secrets.token_urlsafe(32),encoding='utf-8')
env=os.environ.copy();env['XINAYU_SIGN_PASSWORD']=password.read_text(encoding='utf-8').strip()
def run(args):return subprocess.run(args,env=env,check=True,capture_output=True)
if not key.exists():
 run(['java','sun.security.tools.keytool.Main','-genkeypair','-storetype','PKCS12','-keystore',str(key),'-storepass:env','XINAYU_SIGN_PASSWORD',
      '-alias','xinayu','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=XinayuAITranslator, OU=Development, O=Nguyen xuan chien, C=VN'])
certificate=run(['java','sun.security.tools.keytool.Main','-exportcert','-keystore',str(key),'-storepass:env','XINAYU_SIGN_PASSWORD','-alias','xinayu']).stdout
fingerprint=hashlib.sha256(certificate).hexdigest()
if identity.exists() and json.loads(identity.read_text(encoding='utf-8'))['certificate_sha256']!=fingerprint:
 raise SystemExit('Signing identity mismatch; refusing to sign')
identity.write_text(json.dumps({'certificate_sha256':fingerprint,'alias':'xinayu'},indent=2),encoding='utf-8')
a.output.parent.mkdir(parents=True,exist_ok=True)
run(['java','-jar',str(a.apksigner),'sign','--ks',str(key),'--ks-key-alias','xinayu','--ks-pass','env:XINAYU_SIGN_PASSWORD',
     '--key-pass','env:XINAYU_SIGN_PASSWORD','--v1-signing-enabled','false','--v2-signing-enabled','true',
     '--v3-signing-enabled','true','--v4-signing-enabled','false','--out',str(a.output),str(a.input)])
report=run(['java','-jar',str(a.apksigner),'verify','--verbose','--print-certs',str(a.output)]).stdout.decode('utf-8',errors='replace')
if fingerprint not in report.replace(':','').lower():raise SystemExit('Signed APK certificate does not match the retained key')
a.output.with_suffix('.verification.txt').write_text(report,encoding='utf-8')
print('APK signed and verified. Certificate SHA256: '+fingerprint)
