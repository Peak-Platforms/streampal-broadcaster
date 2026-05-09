import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.streampal.broadcaster',
  appName: 'StreamPal Broadcaster',
  webDir: 'src',
  android: {
    buildOptions: {
      keystorePath: 'streampal.keystore',
      keystoreAlias: 'streampal',
    }
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 1000,
      backgroundColor: '#06080a',
      androidScaleType: 'CENTER_CROP',
      showSpinner: false
    }
  }
};

export default config;
