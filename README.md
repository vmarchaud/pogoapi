## PokemonGo's low level api for Java

[![Build Status](https://drone.io/github.com/vmarchaud/pogoapi/status.png)](https://drone.io/github.com/vmarchaud/pogoapi/latest)
[![](https://jitpack.io/v/vmarchaud/pogoapi.svg)](https://jitpack.io/#vmarchaud/pogoapi)

:exclamation: :exclamation: :exclamation:

This API may have issues when the PokemonGO servers are under high load or down, in this case please wait for the official to get back up. You can check the official servers status on [IsPokemonGoDownOrNot.com](http://ispokemongodownornot.com)

This API doesnt fake the official client perfectly, niantic may know that you arent using the official app, we encourage you to use a alternate account to play with this API.

:exclamation: :exclamation: :exclamation:

# How to import

  Import for your favorite dependency manager [using Jitpack](https://jitpack.io/#vmarchaud/pogoapi/)
  Choose the latest version and Jitpack will show you how to import it.
  
  Or if you want to raw jar, go to [releases](https://github.com/vmarchaud/pogoapi/releases) and download the latest jar and add it to the classpath of your IDE.

# Build from source
  - Clone the repo and cd into the folder
  - `` git submodule update --init ``
  - `` ./gradlew build ``
  - you should have the api jar in ``build/libs/pogoapi-0.1.0.jar``

PS : If you want to import the API's source into your IDE, you will need to import the generated protobuf java class into the classpath of your IDE, for example using eclipse :
  - build once : `` ./gradlew build ``
  - Right click on the project
  - Select Build path > Configure Build Path > Source > Add Folder
  - Select `build/generated/source/proto/main/java`
  - Finish

# Usage :

From the fact that people received Cease and Desist letter from The Pokemon Company saying to remove the clientID and clientSecret from their repo, i will not include them in mine. 
You would need to implement the interface `ITokenProvider` to run the client, for example, i can show you [this one](https://gist.github.com/vmarchaud/2eabae971f6d5476e16bcd30e3449ec6) using the PTC login method.

You are free to implement whatever token provider you want, you just need to serve a valid AuthInfo to access Niantic's servers.
Don't hesitate to contact me on the pkre.slack.com (invite bellow) about how to implement a new provider.

After this, using the lib is straight forward as you can see in the example below :
```java
OkHttpClient http = new OkHttpClient();
ITokenProvider provider = // your provider here
NetworkClient client = new NetworkClient(http, provider);

// the GetPlayer doesnt require any input
GetPlayerMessage msg = GetPlayerMessage.newBuilder().build();

// Construct the NetworkRequest with it
NetworkRequest request = new NetworkRequest(RequestType.GET_PLAYER, msg,
				// our callback with java 8 arrow function (work with java 7 code too)
				(result, data) -> {
				 // result is containing a boolean (success or not) and an exception if there is an error
				 // data is a byte array containing the response (will need parsing from protobuf)
				 
					// if it isnt successfull, print the exception
					if (!result.isSuccess()) {
						result.getException().printStackTrace();
						return ;
					}
					// else parse the response and print it
					try {
						GetPlayerResponse response = GetPlayerResponse.parseFrom(data);
						System.out.println(response.toString());
					} catch (InvalidProtocolBufferException e) {
						// this can still happen
						e.printStackTrace();
					}
				});

// queue it to be send
client.offerRequest(request);

!!! WARNING THE CALLBACK WILL BE CALLED FROM THE NetworkHandler THREAD, CARE TO CONCURRENT ANY PROBLEM !!!
!!! Keep in mind too that the http call are sync, so if you send request A before B, the callback of A will be called first.
```

## Contributing
  - Fork it !
  - Create your feature branch: `git checkout -b my-new-feature`
  - Commit your changes: `git commit -am 'Useful information about your new features'`
  - Push to the branch: `git push origin my-new-feature`
  - Submit a pull request on the `development` branch

## Contributors
  - vmarchaud

You can join us in the slack channel #javaapi on the pkre.slack.com ([you can get invited here](https://shielded-earth-81203.herokuapp.com/))
