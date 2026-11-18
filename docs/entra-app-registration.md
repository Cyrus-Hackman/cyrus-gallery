# Microsoft Entra App Registration for Cyrus Gallery

This guide details how to configure Cyrus Gallery in the Microsoft Entra admin center for OneDrive personal authentication.

## 1. Create the App Registration

1. Navigate to the [Microsoft Entra admin center](https://entra.microsoft.com/) or Azure Portal ([portal.azure.com](https://portal.azure.com/)).
2. Go to **Identity** > **Applications** > **App registrations** and select **New registration**.
3. **Name**: `Cyrus Gallery`
4. **Supported account types**: Select **Personal Microsoft accounts only** or **Accounts in any organizational directory and personal Microsoft accounts** (e.g. Skype, Xbox, Outlook.com).
5. Leave the Redirect URI empty for now and click **Register**.
6. Copy the generated **Application (client) ID**.

## 2. Configure Android Platform & Redirect URI

1. In the app registration overview, select **Authentication** under *Manage*.
2. Click **Add a platform** and select **Android**.
3. Fill in:
   - **Package name**: `com.cyrus.gallery`
   - **Signature hash**: Generate using your debug keystore SHA-1:
     ```powershell
     keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore | openssl sha1 -binary | openssl base64
     ```
4. Click **Configure**. Microsoft Entra generates the redirect URI in the format:
   ```
   msauth://com.cyrus.gallery/<Base64SignatureHash>
   ```
5. Update `app/src/main/res/raw/auth_config_single_account.json`:
   - Replace `"client_id"` with your Application (client) ID.
   - Replace `"redirect_uri"` with the generated URI.

## 3. Configure API Permissions

Under **API permissions** > **Add a permission** > **Microsoft Graph** > **Delegated permissions**, add:

| Permission | Reason |
| :--- | :--- |
| `User.Read` | Read user profile name and email address |
| `Files.Read` | Read OneDrive media and folders |
| `Files.Read.All` | Browse media across user's OneDrive drive |
| `offline_access` | Refresh access tokens silently in the background |

## 4. Graph API Endpoints Used

* **Get Drive Root / Delta Sync**: `GET https://graph.microsoft.com/v1.0/me/drive/root/delta`
* **Browse Folder Children**: `GET https://graph.microsoft.com/v1.0/me/drive/items/{folder-id}/children?$select=id,name,size,eTag,cTag,file,folder,image,video,photo,thumbnails`
* **Thumbnails**: `GET https://graph.microsoft.com/v1.0/me/drive/items/{item-id}/thumbnails`
* **Download URL**: `GET https://graph.microsoft.com/v1.0/me/drive/items/{item-id}/content`
