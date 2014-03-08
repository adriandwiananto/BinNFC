package org.adrian.binnfc;

import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener , OnNdefPushCompleteCallback {

	boolean mWriteMode = false;
	private PendingIntent mNfcPendingIntent;
	private NfcAdapter nfcAdapter;
	private NdefMessage toSend;
	private long lrandomACCN;
	private long lLATS;
	byte[] randomIV = new byte[16];
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		/* TextView */
		findViewById(R.id.DebugTextView).setVisibility(View.INVISIBLE);

		TextView ACCNtext = (TextView)findViewById(R.id.ACCNtextview);
		Random ACCN = new Random();
		long ACCNlow = 100000000000000L;
		long ACCNhigh = 281474976710655L;
		lrandomACCN = this.nextLong(ACCN, (ACCNhigh-ACCNlow)) + ACCNlow;
		ACCNtext.setText("ACCN: " + String.valueOf(lrandomACCN));
		
		TextView LATS = (TextView)findViewById(R.id.LATStextview);
		lLATS = System.currentTimeMillis() / 1000;
		lLATS = lLATS - (24*60*60);
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);
		String readableDate = sdf.format(new Date(lLATS*1000));
		LATS.setText("LATS (readable): " + readableDate + "\nLATS (long): "+ String.valueOf(lLATS));
		
		try{
			// Create a secure random number generator using the SHA1PRNG algorithm
			SecureRandom secureRandomGenerator= SecureRandom.getInstance("SHA1PRNG");
			
			// Get 128 random bytes
			secureRandomGenerator.nextBytes(randomIV);
		}catch(NoSuchAlgorithmException e){
			
		}
		
		TextView IV = (TextView)findViewById(R.id.IVtextview);
		IV.setText("IV: " + bytesToHex(randomIV));
		
		
		/* Edit Text */
		EditText SESN = (EditText) findViewById(R.id.SESNTextBox);
		SESN.setFilters(new InputFilter[]{ new InputFilterMinMax("0","999")});

		EditText Amount = (EditText) findViewById(R.id.AmountTextBox);
		Amount.setFilters(new InputFilter[]{ new InputFilterMinMax("0","1000000")});
		
		/* Button */
		Button cancelButton = (Button)findViewById(R.id.CancelButton);
		cancelButton.setEnabled(false);
		cancelButton.setOnClickListener(this);
		
		findViewById(R.id.ProceedButton).setOnClickListener(this);

		nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (nfcAdapter == null) return; 
		nfcAdapter.setNdefPushMessage(null, this);
		nfcAdapter.setOnNdefPushCompleteCallback(this, this);
		
	}
	
	@Override
    public void onResume() {
        super.onResume();
        // Check to see that the Activity started due to an Android Beam
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }
    }
	
	private void enableTagWriteMode() {
	    mWriteMode = true;
	    IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
	    IntentFilter[] mWriteTagFilters = new IntentFilter[] { tagDetected };
	    nfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, mWriteTagFilters, null);		
	}

	private void disableTagWriteMode() {
	    mWriteMode = false;
	    nfcAdapter.disableForegroundDispatch(this);
	}

	@Override
    public void onNewIntent(Intent intent) {
        // onResume gets called after this to handle the intent
        //setIntent(intent);
        
     // Tag writing mode
	    if (mWriteMode && NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
	        Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
	        if (writeTag(toSend, detectedTag)) {
	            Toast.makeText(this, "Success: Wrote data to nfc tag", Toast.LENGTH_LONG).show();
				findViewById(R.id.ProceedButton).setEnabled(true);			
				findViewById(R.id.CancelButton).setEnabled(false);
				findViewById(R.id.DebugTextView).setVisibility(View.INVISIBLE);
	            
	        } 
	    }
    }
	
	private void processIntent(Intent intent) {
		// TODO Auto-generated method stub
		Toast.makeText(getApplicationContext(), "app launched by beam", Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch(v.getId()){
		case R.id.ProceedButton:
			if(((EditText) findViewById(R.id.AmountTextBox)).getText().toString().length() > 0) {
				if(((EditText) findViewById(R.id.SESNTextBox)).getText().toString().length() == 3) {
					if(((EditText) findViewById(R.id.KeyTextBox)).getText().toString().length() == 64){
						//sendNDEF();
						nfcAdapter = NfcAdapter.getDefaultAdapter(MainActivity.this);
						mNfcPendingIntent = PendingIntent.getActivity(MainActivity.this, 0, new Intent(MainActivity.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
						
						toSend = buildNDEFmsg();
						enableTagWriteMode();
						
						findViewById(R.id.ProceedButton).setEnabled(false);			
						findViewById(R.id.CancelButton).setEnabled(true);						
					}
					else{
						Toast.makeText(getApplicationContext(), "Please input 64 bit key", Toast.LENGTH_LONG).show();
					}
				}
				else {
					Toast.makeText(getApplicationContext(), "fill SESN between 100-999", Toast.LENGTH_LONG).show();					
				}
			}			
			else {
				Toast.makeText(getApplicationContext(), "fill amount ( < 1.000.000 )", Toast.LENGTH_LONG).show();
			}
			break;
		case R.id.CancelButton:
			Toast.makeText(getApplicationContext(), "Cancel pressed", Toast.LENGTH_LONG).show();
			disableTagWriteMode();
			findViewById(R.id.ProceedButton).setEnabled(true);			
			findViewById(R.id.CancelButton).setEnabled(false);
			findViewById(R.id.DebugTextView).setVisibility(View.INVISIBLE);
		}
	}

	private NdefMessage buildNDEFmsg() {
		// TODO Auto-generated method stub
		EditText Amount = (EditText)findViewById(R.id.AmountTextBox);
		EditText SESN = (EditText)findViewById(R.id.SESNTextBox);
		//Toast.makeText(getApplicationContext(), "Amount:"+Amount.getText().toString()+" SESN:"+SESN.getText().toString(), Toast.LENGTH_SHORT).show();
		
		int intAmount = Integer.parseInt(Amount.getText().toString());
		int intSESN = Integer.parseInt(SESN.getText().toString());
		byte[] trans = new byte[55];
		
		trans[0] = 55;	//Frame Length (1)
		trans[1] = 1;	//offline (1)
		trans[2] = 0;	//payer (1)
		System.arraycopy(ByteBuffer.allocate(4).putInt(intSESN).array(), 2, trans, 3, 2); //SESN (2)
		trans[5] = 0; //EH (2)
		trans[6] = 0; //EH
		System.arraycopy(ByteBuffer.allocate(8).putLong(lrandomACCN).array(),2, trans, 7, 6); //ACCN(6)
		//System.arraycopy(ByteBuffer.allocate(8).putLong(System.currentTimeMillis()/1000).array(),4, trans, 13, 4); //TS(4)
		System.arraycopy(ByteBuffer.allocate(8).putLong(lLATS+(24*60*60)).array(),4, trans, 13, 4); //TS(4)
		System.arraycopy(ByteBuffer.allocate(4).putInt(intAmount).array(), 0, trans, 17, 4); //Amount(4)
		System.arraycopy(ByteBuffer.allocate(8).putLong(lLATS).array(),4, trans, 21, 4); //LATS(4)
		System.arraycopy(ByteBuffer.allocate(4).putInt(intSESN).array(), 2, trans, 25, 2); //SESN(2)
		Arrays.fill(trans, 27, 39, (byte) 12); //PAD(12)
		System.arraycopy(randomIV, 0, trans, 39, 16);
		
		byte[] encAES = new byte[20];
		System.arraycopy(trans, 7, encAES, 0, 20);
		
		EditText aeskey = (EditText)findViewById(R.id.KeyTextBox);
		byte[] keyAES = hexStringToByteArray(aeskey.getEditableText().toString());
		
		byte[] ciphertext = null;
		try {
			ciphertext = AES256cipher.encrypt(randomIV, keyAES, encAES);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
		byte[] decryptedCiphertext = null;
		try {
			decryptedCiphertext = AES256cipher.decrypt(randomIV, keyAES, ciphertext);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
		byte[] transFinal = new byte[55]; 
		if(Arrays.equals(encAES, decryptedCiphertext)) {
			System.arraycopy(trans, 0, transFinal, 0, 7);
			System.arraycopy(ciphertext, 0, transFinal, 7, 32);
			System.arraycopy(randomIV, 0, transFinal, 39, 16);
		}
		
		String transFinalStr = bytesToHex(transFinal); 
		String plaintext = bytesToHex(encAES);
		String encrypted = bytesToHex(ciphertext);
		String decrypted = bytesToHex(decryptedCiphertext);
		
		TextView debug = (TextView)findViewById(R.id.DebugTextView);
		debug.setVisibility(View.VISIBLE);
		debug.setText("to send: " + transFinalStr + "\n\nplaintext: " + plaintext + "\n\nencrypted: " + encrypted + "\n\ndecrypted: " + decrypted);
		
		//NdefRecord rec = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, "org.adrian.binnfc".getBytes(Charset.forName("US-ASCII")), new byte[0], trans);
		//NdefRecord aar = new NdefRecord(NdefRecord.createApplicationRecord("org.adrian.binnfc"));
		NdefMessage msg =  new NdefMessage(new NdefRecord[]{createNDEFRecord("data/test", transFinal)});
		//nfcAdapter.setNdefPushMessage(msg, this);
		return msg;
	}
	
	private NdefRecord createNDEFRecord(String mime, byte[] payload) {
		byte[] mimeb = mime.getBytes(Charset.forName("US-ASCII"));
		NdefRecord mrec = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, mimeb, new byte[0], payload);
		return mrec;
	}
	
	long nextLong(Random rng, long n) {
	   // error checking and 2^x checking removed for simplicity.
	   long bits, val;
	   do {
	      bits = (rng.nextLong() << 1) >>> 1;
	      val = bits % n;
	   } while (bits-val+(n-1) < 0L);
	   return val;
	}
	
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}

	public static byte[] hexStringToByteArray(String s) {
	    int len = s.length();
	    byte[] data = new byte[len / 2];
	    for (int i = 0; i < len; i += 2) {
	        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
	                             + Character.digit(s.charAt(i+1), 16));
	    }
	    return data;
	}
	
	@Override
	public void onNdefPushComplete(NfcEvent arg0) {
		// TODO Auto-generated method stub
		findViewById(R.id.ProceedButton).setEnabled(true);			
		findViewById(R.id.CancelButton).setEnabled(false);			
	}
	
	/*
	* Writes an NdefMessage to a NFC tag
	*/
	public boolean writeTag(NdefMessage message, Tag tag) {
	    int size = message.toByteArray().length;
	    try {
	        Ndef ndef = Ndef.get(tag);
	        if (ndef != null) {
	            ndef.connect();
	            if (!ndef.isWritable()) {
					Toast.makeText(getApplicationContext(),
					"Error: tag not writable",
					Toast.LENGTH_SHORT).show();
	                return false;
	            }
	            if (ndef.getMaxSize() < size) {
					Toast.makeText(getApplicationContext(),
					"Error: tag too small",
					Toast.LENGTH_SHORT).show();
	                return false;
	            }
	            ndef.writeNdefMessage(message);
	            return true;
	        } else {
	            NdefFormatable format = NdefFormatable.get(tag);
	            if (format != null) {
	                try {
	                    format.connect();
	                    format.format(message);
	                    return true;
	                } catch (IOException e) {
	                    return false;
	                }
	            } else {
	                return false;
	            }
	        }
	    } catch (Exception e) {
	        return false;
	    }
	}
}

