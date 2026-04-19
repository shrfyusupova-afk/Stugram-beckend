import 'package:flutter/material.dart';

class ProfileButton extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return IconButton(
      icon: Icon(Icons.person),
      onPressed: () {
        // Prevent app exit
        Navigator.pushNamed(context, '/profile');
      },
    );
  }
}
