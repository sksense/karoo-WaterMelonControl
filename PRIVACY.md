# Privacy

WaterMelonControl processes media state locally on the Karoo device.

## Notification access

WaterMelonControl requests Android Notification Access so it can discover active media sessions and control the currently active sideloaded media app.

The app uses this access for media information such as:

- track title
- artist
- playback state
- active media application

## Data handling

WaterMelonControl:

- does not upload notification or media information
- does not store notification contents
- does not include analytics or advertising
- does not track users
- does not require an account
- does not send media information to the developer

Media state remains in memory on the Karoo and is used to update local widgets.

## Network access

WaterMelonControl does not request Android internet permission. Release/update metadata is handled by the Karoo extension system through the public release manifest.

## Karoo 2 compatibility

Karoo 2 fallback handling listens for older Android media broadcasts. These events are processed locally and are not recorded or uploaded.

## Questions

Privacy questions can be opened through the project issue tracker:

https://github.com/sksense/karoo-WaterMelonControl/issues
