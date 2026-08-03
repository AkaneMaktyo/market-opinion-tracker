/// <reference types="@capawesome/capacitor-live-update" />

import type { CapacitorConfig } from '@capacitor/cli';

const liveUpdatePublicKey = '-----BEGIN PUBLIC KEY-----\n'
  + 'MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEA5eAxaNif7pRjTaRmPNvV\n'
  + '9PHtEriByfTwVw7WgaDUi0y6FoY9SDEytxPj6apFAE0aHuniLL4tjsmOErAcBtyC\n'
  + 'jUBoglGb04T04TFO59bB0TZGvObzQLwzq2zp3gAfv1SYLU4GxA92ist7Fb/ThWh9\n'
  + 'IFOdxMJNAEp7BCfpZvIH1UxWZfuigD9BXiRPIk9Tce9n/0V0TqAQUztPjoH+jjd1\n'
  + 'Uc+GObbKs4Sx+EMseJLNqzQXY7noJLY3QBXu+/zpEEQcWpbHW+OEufCznMTkktZt\n'
  + 'VacpgZDeb7O5sg0QZAYa/914icGBgmJxuotJwjooAZzMb2MGrWmKNAXP5IKG/KZm\n'
  + 't65UdRJt3UuLSt8FkY72g8+G5SX7JO9IJK6Dj90SVlPNIOmHFvjDmNYWFa35KH0N\n'
  + 'J3BiTFI84aXXsgAPoScHMqza2k27iNakEvXBcwWmsQZ2O+dGlYK0CNmeOLFIcPjI\n'
  + 'BgmRH7AKDUjj2Dickmcpm7vWDdZSA50I7U1F6ZPsuUENAgMBAAE=\n'
  + '-----END PUBLIC KEY-----\n';

const config: CapacitorConfig = {
  appId: 'com.personal.marketopiniontracker',
  appName: '美股观点追踪',
  webDir: 'dist',
  backgroundColor: '#e5e7eb',
  loggingBehavior: 'debug',
  android: {
    backgroundColor: '#e5e7eb',
  },
  plugins: {
    CapacitorHttp: {
      enabled: true,
    },
    LiveUpdate: {
      autoBlockRolledBackBundles: true,
      autoDeleteBundles: true,
      autoUpdateStrategy: 'none',
      httpTimeout: 60000,
      publicKey: liveUpdatePublicKey,
      readyTimeout: 10000,
    },
  },
};

export default config;
