# VoiceWaveView

[![License](https://img.shields.io/badge/license-Apache%202-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Download](https://api.bintray.com/packages/echohaha/maven/library/images/download.svg) ](https://bintray.com/echohaha/maven/library/_latestVersion)

**VoiceWaveView** - An Android library that provides a voice wave effect.

## Sample

<img src="http://chuantu.biz/t5/88/1494947367x2890174231.gif" alt="sample" title="sample" width="300" height="450" />
<img src="http://chuantu.biz/t5/88/1494948148x2890174046.gif" alt="sample" title="sample" width="300" height="450" />

## Usage

**For a working implementation of this project see the `sample/` folder.**

### Dependency

Include the library as local library project or add the dependency in your build.gradle.

```groovy
dependencies {
    compile 'me.kankei.voicewaveview:library:0.0.1'
}
```

### Layout

Include the VoiceWaveView widget in your layout. And you can customize it like this. this view doesn't support `wrap_content`, Its size must be determined or `match_parent`.  
   
```xml
<me.kaneki.voicewaveview.VoiceWaveView
        android:id="@+id/voiceWaveView"
        android:layout_width="300dp"
        android:layout_height="40dp"
        android:layout_centerHorizontal="true"
        android:background="@drawable/msg_voice_panel_bg"
        app:lineWidth="1.5dp"
        app:dividerWidth="1dp"
        app:duration="30"
        app:refreshRatio="50"
        app:backgroundColor="#7f7f7f"
        app:activeLineColor="#ffffff"
        app:inactiveLineColor="#99ffffff"/>         
```

### Java

If you want to record or play the recorded data, you can write these code, etc in your Activity.

```java
    VoiceWaveView mVoiceWaveView = (VoiceWaveView) findViewById(R.id.voiceWaveView);
    
    //begin record
    mVoiceWaveView.startRecord();
    //set the record wave height percet (1-100), it can be used during other thread.
    mVoiceWaveView.setWaveHeightPercent(random.nextInt(100) + 1);
    //stop record
    mVoiceWaveView.stopRecord();
    //get the last record data, you can change the list to json string or other type to save it
    ArrayList<WaveBean> list = mVoiceWaveView.getLastWaveData();
    
    //start play, if the list is null, it will play the last record data
    mVoiceWaveView.starPlay(list);
    //pause the play or resume it
    mVoiceWaveView.pauseOrResumePlay();
    
```

## Customization

|name|format|description|
|:---:|:---:|:---:|
| backgroundColor | color |background color, default is `#7f7f7`
| activeLineColor | color | active line color, default is `#ffffff`
| inactiveLineColor | color | inactive line color, default is `#99ffffff`
| lineWidth | dimension | wave line width, default is `1dp`
| dividerWidth | dimension | divider width between two wave line, default is `1dp`
| duration | integer | max record time , default is `30s`
| refreshRatio | integer | view refresh ratio, default is `50ms`
 


**All attributes have their respective getters and setters to change them at runtime.**

## Change Log

### 0.0.1（2017-05-17）
- library first build


## Community

Looking for contributors, feel free to fork !

Tell me if you're using my library in your application, I'll share it in this README.

## Contact Me

I work as Android Development Engineer at Meili-inc Group.

If you have any questions or want to make friends with me, please feel free to contact me : [549829925#qq.com](mailto:imtangqi@qq.com "Welcome to contact me")


## License

    Copyright 2016 Kaneki

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
