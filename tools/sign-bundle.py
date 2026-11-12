"""Sign a Play AAB locally using the same retained key; never upload key material."""
import argparse,os,subprocess,json,hashlib
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--input',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--key-dir',type=Path,required=True)
a=p.parse_args();key=a.key_dir/'XinayuAITranslator-debug.p12';password=a.key_dir/'store-password.txt';identity=a.key_dir/'identity.json'
if not all(x.is_file() for x in (a.input,key,password,identity)):raise SystemExit('Restore the retained key directory and provide the unsigned AAB first')
env=os.environ.copy();env['XINAYU_SIGN_PASSWORD']=password.read_text(encoding='utf-8').strip()
def run(args):return subprocess.run(args,env=env,capture_output=True,check=True)
cert=run(['java','sun.security.tools.keytool.Main','-exportcert','-keystore',str(key),'-storepass:env','XINAYU_SIGN_PASSWORD','-alias','xinayu']).stdout
expected=json.loads(identity.read_text(encoding='utf-8'))['certificate_sha256']
if hashlib.sha256(cert).hexdigest()!=expected:raise SystemExit('Retained key identity mismatch')
a.output.parent.mkdir(parents=True,exist_ok=True)
command=['java','-Duser.language=en','-Dfile.encoding=UTF-8','-m','jdk.jartool/sun.security.tools.jarsigner.Main']
run(command+['-keystore',str(key),'-storepass:env','XINAYU_SIGN_PASSWORD','-keypass:env','XINAYU_SIGN_PASSWORD',
             '-sigalg','SHA256withRSA','-digestalg','SHA-256','-signedjar',str(a.output),str(a.input),'xinayu'])
report=run(command+['-verify','-verbose','-certs',str(a.output)]).stdout.decode('utf-8',errors='replace')
if 'jar verified.' not in report:raise SystemExit('Bundle signature was not verified')
a.output.with_suffix('.verification.txt').write_text(report,encoding='utf-8')
print('Signed AAB verified. Upload certificate SHA256: '+expected)
