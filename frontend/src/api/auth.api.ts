import axios from 'axios';

export const login = async (username: string, password: string): Promise<string> => {
  const { data } = await axios.post('/cognito/', {
    AuthFlow: 'USER_PASSWORD_AUTH',
    ClientId: import.meta.env.VITE_COGNITO_CLIENT_ID,
    AuthParameters: {
      USERNAME: username,
      PASSWORD: password,
    },
  }, {
    headers: {
      'Content-Type': 'application/x-amz-json-1.1',
      'X-Amz-Target': 'AWSCognitoIdentityProviderService.InitiateAuth',
    },
  });
  return data.AuthenticationResult.IdToken as string;
};
