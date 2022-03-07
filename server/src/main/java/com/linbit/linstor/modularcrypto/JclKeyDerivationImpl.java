package com.linbit.linstor.modularcrypto;

import com.linbit.ImplementationError;
import com.linbit.crypto.KeyDerivation;
import com.linbit.linstor.LinStorException;
import com.linbit.utils.UnicodeConversion;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Arrays;

public class JclKeyDerivationImpl implements KeyDerivation
{
    private final SecretKeyFactory keyFact;

    public static final int ITERATIONS  = 5000;
    public static final int HASH_SIZE   = 512;

    JclKeyDerivationImpl()
        throws LinStorException
    {
        try
        {
            keyFact = SecretKeyFactory.getInstance("PBKDF2WITHHMACSHA512");
        }
        catch (NoSuchAlgorithmException algExc)
        {
            throw new LinStorException(
                "Cryptography initialization error: Cannot initialize key derivation algorithm",
                "Cryptography initialization error: Cannot initialize key derivation algorithm",
                "The requested algorithm is not available on this platform",
                "Make sure that the required cryptographic extensions are available in the\n" +
                "Java virtual machine",
                "The requested cryptography algorithm was PBKDF2 with HMAC-SHA2-512",
                algExc
            );
        }
    }

    @Override
    public byte[] passphraseToKey(
        byte[] passphrase,
        byte[] salt
    )
        throws LinStorException
    {
        final SecretKey derivedKey;
        try
        {
            if (passphrase != null && salt != null)
            {
                final char[] passphraseChars;
                try
                {
                    passphraseChars = UnicodeConversion.utf8BytesToUtf16Chars(passphrase, true);
                }
                catch (UnicodeConversion.InvalidSequenceException exc)
                {
                    throw new LinStorException(
                        "The passphrase contains a byte sequence that is not a valid UTF-8 sequence"
                    );
                }
                PBEKeySpec keySpec = new PBEKeySpec(passphraseChars, salt, ITERATIONS, HASH_SIZE);

                synchronized (keyFact)
                {
                    try
                    {
                        derivedKey = keyFact.generateSecret(keySpec);
                    }
                    catch (InvalidKeySpecException exc)
                    {
                        throw new ImplementationError(
                            "The PBKDF2 key derivation generated an InvalidKeySpecException", exc
                        );
                    }
                }
            }
            else
            {
                throw new ImplementationError(
                    getClass().getSimpleName() + " method passphraseToKey called " +
                    "with a null pointer argument"
                );
            }
        }
        finally
        {
            clearDataFields(passphrase, salt);
        }
        final byte[] derivedKeyData = derivedKey.getEncoded();
        return derivedKeyData;
    }

    /**
     * Clears the specified byte arrays by setting all elements to zero.
     *
     * Any element of {@code dataFieldList} may be a null reference.
     * The {@code dataFieldList} argument itself may NOT be a null reference.
     * @param dataFieldList The list of byte arrays to clear
     */
    static void clearDataFields(byte[]... dataFieldList)
    {
        for (byte[] dataField : dataFieldList)
        {
            if (dataField != null)
            {
                Arrays.fill(dataField, (byte) 0);
            }
        }
    }
}
